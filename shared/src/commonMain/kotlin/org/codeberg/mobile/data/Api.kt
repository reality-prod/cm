// SPDX-License-Identifier: AGPL-3.0-or-later
package org.codeberg.mobile.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readRemaining
import kotlin.random.Random
import kotlinx.io.readByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.URLProtocol
import io.ktor.http.appendPathSegments
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.util.encodeBase64
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

private const val HOST = "codeberg.org"
private const val MAX_BODY = 10L * 1024 * 1024
private val LOGIN = Regex("^[A-Za-z0-9_.-]{1,40}$")

// Verified against forgejo.org/docs (token-scope, api-usage): X-Forgejo-OTP header, "token" auth scheme, the token
// response fields id/sha1, and the read:/write: scope names. Least privilege: only what the app uses today.
private val READ_SCOPES = listOf("read:repository", "read:issue", "read:notification", "read:user")

sealed interface ApiError {
    data object Network : ApiError
    data object Auth : ApiError
    data object TwoFactor : ApiError
    data object SecurityKey : ApiError
    data object Challenge : ApiError
    data object RateLimited : ApiError
    data object NotFound : ApiError
    data class Unexpected(val code: Int) : ApiError
}

class ApiException(val error: ApiError) : Exception(error.toString())

fun errorText(e: ApiError): String = when (e) {
    ApiError.Auth -> "Wrong username or password."
    ApiError.TwoFactor -> "That code didn't work. Codes rotate every 30 seconds; check your clock."
    ApiError.SecurityKey -> "Your account uses a security key or external login, which Codeberg doesn't allow apps " +
        "to use with a password. Browser sign-in is planned."
    ApiError.Challenge, ApiError.RateLimited -> "Codeberg is limiting automated traffic. Try again later."
    ApiError.Network -> "Can't reach codeberg.org. Check your connection."
    ApiError.NotFound -> "Not found."
    is ApiError.Unexpected -> "Unexpected response (${e.code})."
}

@Serializable
data class Repo(
    @SerialName("full_name") val fullName: String = "",
    val description: String = "",
    val language: String = "",
    @SerialName("stars_count") val stars: Int = 0,
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("private") val isPrivate: Boolean = false,
)

@Serializable private data class RepoSearch(val data: List<Repo> = emptyList())
@Serializable private data class UserDto(val login: String = "")
@Serializable private data class TokenDto(val id: Long = 0, val sha1: String? = null, val token: String? = null)

class Page<T>(val items: List<T>, val hasMore: Boolean)
class RawResult(val body: String, val hasMore: Boolean)
class Session(val login: String, val token: String, val tokenId: Long)

/**
 * Same name as Ktor's bodyAsText on purpose: with the Ktor import removed, every call in this package resolves to this
 * version, which never buffers more than MAX_BODY bytes, even for chunked responses without a Content-Length.
 */
internal suspend fun HttpResponse.bodyAsText(): String {
    val bytes = bodyAsChannel().readRemaining(MAX_BODY + 1).readByteArray()
    if (bytes.size > MAX_BODY) throw ApiException(ApiError.Unexpected(0))
    return bytes.decodeToString()
}

private fun randomSuffix(): String = Random.nextInt(0x1000000).toString(16).padStart(6, '0')

class ForgejoApi(
    private val token: () -> String?,
    private val onExpired: () -> Unit,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    private val http = HttpClient {
        followRedirects = false // The API needs none; never let a redirect carry credentials elsewhere.
        expectSuccess = false
        install(UserAgent) { agent = "CodebergMobile/0.2.0 (unofficial client)" } // Honest, never "Mozilla".}
        install(HttpRequestRetry) {
            maxRetries = 2
            retryIf { request, response ->
                request.method == HttpMethod.Get && response.status.value in setOf(429, 502, 503, 504)
            }
            exponentialDelay()
        }
    }

    private suspend fun call(block: HttpRequestBuilder.() -> Unit): HttpResponse {
        try {
            val r = http.request {
                url { protocol = URLProtocol.HTTPS; host = HOST } // Fixed host: tokens only ever go to Codeberg.
                header(HttpHeaders.Accept, "application/json")
                block()
            }
            if (r.contentType()?.match(ContentType.Text.Html) == true) throw ApiException(ApiError.Challenge)
            if ((r.contentLength() ?: 0L) > MAX_BODY) throw ApiException(ApiError.Unexpected(0))
            return r
        } catch (e: ApiException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ApiException(ApiError.Network)
        }
    }

    // TODO(verify-api): 2FA and security-key detection is by message text.
    private fun fail(r: HttpResponse, body: String): Nothing {
        val b = body.lowercase()
        throw ApiException(
            when {
                r.status.value == 429 -> ApiError.RateLimited
                "security key" in b -> ApiError.SecurityKey
                "otp" in b || "two-factor" in b -> ApiError.TwoFactor
                r.status.value == 401 -> ApiError.Auth
                r.status.value == 404 -> ApiError.NotFound
                else -> ApiError.Unexpected(r.status.value)
            },
        )
    }

    private inline fun <reified T> decode(body: String): T = try {
        json.decodeFromString<T>(body)
    } catch (e: SerializationException) {
        throw ApiException(ApiError.Unexpected(200))
    }

    suspend fun searchRepos(query: String, page: Int): Page<Repo> {
        val t = token()
        val r = call {
            url {
                appendPathSegments("api", "v1", "repos", "search")
                parameters.append("page", page.toString())
                parameters.append("limit", "30")
                parameters.append("sort", "updated")
                parameters.append("order", "desc")
                if (query.isNotBlank()) parameters.append("q", query)
            }
            if (t != null) header(HttpHeaders.Authorization, "token $t")
        }
        val body = r.bodyAsText()
        if (!r.status.isSuccess()) {
            if (r.status.value == 401 && t != null) onExpired()
            fail(r, body)
        }
        return Page(decode<RepoSearch>(body).data, r.headers[HttpHeaders.Link]?.contains("rel=\"next\"") == true)
    }

    /**
     * Authenticated GET of /api/v1/[segments]. Each segment is percent-encoded individually (slashes too), and
     * callers must pass validated segments (see Safe.kt). The token is only attached here, to the fixed host.
     */
    suspend fun getText(segments: List<String>, params: Map<String, String> = emptyMap()): RawResult {
        val t = token()
        val r = call {
            url {
                appendPathSegments(listOf("api", "v1") + segments, encodeSlash = true)
                params.forEach { (k, v) -> parameters.append(k, v) }
            }
            if (t != null) header(HttpHeaders.Authorization, "token $t")
        }
        val body = r.bodyAsText()
        if (!r.status.isSuccess()) {
            if (r.status.value == 401 && t != null) onExpired()
            fail(r, body)
        }
        return RawResult(body, r.headers[HttpHeaders.Link]?.contains("rel=\"next\"") == true)
    }

    /**
     * Deletes tokens on Codeberg so a stolen copy stops working. Forgejo requires Basic auth for this (a token cannot
     * delete tokens), so the password is needed once and is not kept. An already-deleted token (404) counts as done.
     */
    suspend fun revokeTokens(user: String, password: String, otp: String?, ids: List<Long>) {
        if (!LOGIN.matches(user) || user == "." || user == "..") throw ApiException(ApiError.Auth)
        val basic = "Basic " + "$user:$password".encodeBase64()
        for (id in ids.filter { it > 0 }) {
            val r = call {
                method = HttpMethod.Delete
                url { appendPathSegments("api", "v1", "users", user, "tokens", id.toString()) }
                header(HttpHeaders.Authorization, basic)
                if (otp != null) header("X-Forgejo-OTP", otp)
            }
            val body = r.bodyAsText()
            if (!r.status.isSuccess() && r.status.value != 404) fail(r, body)
        }
    }

    /** Write request authenticated with an explicitly passed, elevated token. Never uses the stored read token. */
    suspend fun send(method: HttpMethod, segments: List<String>, params: Map<String, String>, writeToken: String) {
        val r = call {
            this.method = method
            url {
                appendPathSegments(listOf("api", "v1") + segments, encodeSlash = true)
                params.forEach { (k, v) -> parameters.append(k, v) }
            }
            header(HttpHeaders.Authorization, "token $writeToken")
        }
        if (!r.status.isSuccess()) fail(r, r.bodyAsText())
    }

    /**
     * Native sign-in: Basic auth (+ optional OTP), then the app creates its own token with exactly [scopes].
     * Nothing is stored here; the caller decides where the token lives.
     */
    suspend fun signIn(
        user: String,
        password: String,
        otp: String?,
        scopes: List<String> = READ_SCOPES,
        tokenName: String = "Codeberg mobile",
    ): Session {
        if (user.isBlank() || user.length > 254 || ':' in user || user.any { it.isISOControl() }) {
            throw ApiException(ApiError.Auth)
        }
        val basic = "Basic " + "$user:$password".encodeBase64()
        fun HttpRequestBuilder.creds() {
            header(HttpHeaders.Authorization, basic)
            if (otp != null) header("X-Forgejo-OTP", otp)
        }

        val me = call { url { appendPathSegments("api", "v1", "user") }; creds() }
        val meBody = me.bodyAsText()
        if (!me.status.isSuccess()) fail(me, meBody)
        val login = decode<UserDto>(meBody).login
        // The login comes from the server: validate it before it goes into a credentialed URL path.
        if (!LOGIN.matches(login) || login == "." || login == "..") throw ApiException(ApiError.Unexpected(0))

        val payload = buildJsonObject {
            put("name", "$tokenName (${randomSuffix()})") // Names must be unique per user.
            putJsonArray("scopes") { scopes.forEach { add(it) } }
        }.toString()
        val tr = call {
            method = HttpMethod.Post
            url { appendPathSegments("api", "v1", "users", login, "tokens") }
            creds()
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val trBody = tr.bodyAsText()
        if (!tr.status.isSuccess()) fail(tr, trBody)
        val dto = decode<TokenDto>(trBody)
        return Session(login, dto.sha1 ?: dto.token ?: throw ApiException(ApiError.Unexpected(0)), dto.id)
    }
}
