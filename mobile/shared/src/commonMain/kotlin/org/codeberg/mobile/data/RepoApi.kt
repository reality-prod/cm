// SPDX-License-Identifier: AGPL-3.0-or-later
package org.codeberg.mobile.data

import io.ktor.http.HttpMethod
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

// TODO(verify-api): field names follow the Gitea/Forgejo swagger; unverified against live Codeberg.
/** The account that owns a repository or user. avatar_url is an absolute Codeberg URL. */
@Serializable
data class RepoOwner(
    val login: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
)

@Serializable data class UserRef(val login: String = "")

@Serializable
data class RepoDetail(
    @SerialName("full_name") val fullName: String = "",
    val description: String = "",
    @SerialName("stars_count") val stars: Int = 0,
    @SerialName("forks_count") val forks: Int = 0,
    @SerialName("open_issues_count") val openIssues: Int = 0,
    @SerialName("default_branch") val defaultBranch: String = "",
    val language: String = "",
    val archived: Boolean = false,
    val owner: RepoOwner = RepoOwner(),
)

@Serializable
data class Entry(val name: String = "", val path: String = "", val type: String = "", val size: Long = 0)

@Serializable
data class Issue(
    val number: Int = 0,
    val title: String = "",
    val body: String = "",
    val state: String = "",
    val comments: Int = 0,
    @SerialName("created_at") val createdAt: String = "",
    val user: UserRef = UserRef(),
)

@Serializable
data class Comment(
    val body: String = "",
    @SerialName("created_at") val createdAt: String = "",
    val user: UserRef = UserRef(),
)

/** The signed-in account, from GET /user. full_name may be empty; fall back to the login. */
@Serializable
data class Profile(
    val login: String = "",
    @SerialName("full_name") val fullName: String = "",
    val description: String = "",
    val location: String = "",
    val website: String = "",
    @SerialName("followers_count") val followers: Int = 0,
    @SerialName("following_count") val following: Int = 0,
) {
    /** What to call the person on screen: their name if they have one, otherwise their username. */
    val displayName: String get() = fullName.ifBlank { login }
}

@Serializable data class NotifSubject(val title: String = "", val type: String = "", val url: String = "")
@Serializable data class NotifRepo(@SerialName("full_name") val fullName: String = "")

@Serializable
data class Notif(
    val id: Long = 0,
    val unread: Boolean = false,
    @SerialName("updated_at") val updatedAt: String = "",
    val subject: NotifSubject = NotifSubject(),
    val repository: NotifRepo = NotifRepo(),
)

private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

private inline fun <reified T> parse(body: String): T = try {
    json.decodeFromString<T>(body)
} catch (e: SerializationException) {
    throw ApiException(ApiError.Unexpected(200))
}

private fun repoSegments(full: String) = listOf("repos") + requireSafeRepo(full)

/** The signed-in account. Needs read:user. */
suspend fun ForgejoApi.currentUser(): Profile = parse(getText(listOf("user")).body)

/** Needs read:repository. */
suspend fun ForgejoApi.myRepos(page: Int): Page<Repo> {
    val r = getText(listOf("user", "repos"), mapOf("page" to page.toString(), "limit" to "30"))
    return Page(parse(r.body), r.hasMore)
}

/** Needs read:notification. TODO(verify-api): `all` semantics. */
suspend fun ForgejoApi.notifications(page: Int, all: Boolean): Page<Notif> {
    val r = getText(listOf("notifications"), mapOf("page" to page.toString(), "limit" to "30", "all" to all.toString()))
    return Page(parse(r.body), r.hasMore)
}

/** PHASE 2 write call. Needs a write:notification token, passed in explicitly; never the stored read token. */
suspend fun ForgejoApi.markRead(id: Long, writeToken: String) =
    send(HttpMethod.Patch, listOf("notifications", "threads", id.toString()), mapOf("to-status" to "read"), writeToken)

suspend fun ForgejoApi.repoDetail(full: String): RepoDetail = parse(getText(repoSegments(full)).body)

/** Lists a folder on the default branch. An empty [path] is the repository root. */
suspend fun ForgejoApi.listDir(full: String, path: String): List<Entry> =
    parse(getText(repoSegments(full) + "contents" + safePathSegments(path)).body)

/** Raw file text on the default branch. TODO(verify-api): confirm /raw/{filepath}. */
suspend fun ForgejoApi.rawFile(full: String, path: String): String {
    val segments = safePathSegments(path)
    if (segments.isEmpty()) throw ApiException(ApiError.Unexpected(0))
    return getText(repoSegments(full) + "raw" + segments).body
}

/** [type] is "issues" or "pulls"; [state] is "open" or "closed". */
suspend fun ForgejoApi.issues(full: String, type: String, state: String, page: Int): Page<Issue> {
    val r = getText(
        repoSegments(full) + "issues",
        mapOf("type" to type, "state" to state, "page" to page.toString(), "limit" to "30"),
    )
    return Page(parse(r.body), r.hasMore)
}

suspend fun ForgejoApi.issue(full: String, number: Int): Issue =
    parse(getText(repoSegments(full) + "issues" + number.toString()).body)

suspend fun ForgejoApi.comments(full: String, number: Int): List<Comment> =
    parse(getText(repoSegments(full) + "issues" + number.toString() + "comments").body)
