// SPDX-License-Identifier: AGPL-3.0-or-later
package org.codeberg.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.codeberg.mobile.data.safeHttpUrl
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.MarkdownParser

private const val MAX_CHARS = 200_000
private const val MAX_DEPTH = 10

private class Md(
    val src: String,
    val linkColor: Color,
    val inlineBg: Color,
    val blockBg: Color,
    val muted: Color,
    val onLink: (String) -> Unit,
)

/**
 * Renders untrusted Markdown natively. No WebView or JavaScript; raw HTML is dropped; images are never fetched
 * (no IP leak or tracking pixels); only validated http(s) links are clickable, and the app confirms before opening.
 * TODO: render tables as a grid, syntax highlighting, repo-relative links.
 */
@Composable
fun Markdown(text: String, onLink: (String) -> Unit, modifier: Modifier = Modifier) {
    val src = remember(text) { text.take(MAX_CHARS) }
    val root = remember(src) { MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(src) }
    val s = MaterialTheme.colorScheme
    val md = Md(src, s.primary, s.outlineVariant, s.background, s.onSurfaceVariant, onLink)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) { Blocks(root, md, 0) }
}

@Composable
private fun Blocks(parent: ASTNode, md: Md, depth: Int) {
    if (depth > MAX_DEPTH) return
    parent.children.forEach { Block(it, md, depth) }
}

private fun content(node: ASTNode): ASTNode =
    node.children.firstOrNull { it.type == MarkdownTokenTypes.ATX_CONTENT || it.type == MarkdownTokenTypes.SETEXT_CONTENT }
        ?: node

@Composable
private fun Block(node: ASTNode, md: Md, depth: Int) {
    val ty = MaterialTheme.typography
    when (node.type) {
        MarkdownElementTypes.PARAGRAPH -> InlineText(node, md, ty.bodyMedium)
        MarkdownElementTypes.ATX_1, MarkdownElementTypes.SETEXT_1 -> InlineText(content(node), md, ty.headlineMedium, true)
        MarkdownElementTypes.ATX_2, MarkdownElementTypes.SETEXT_2 -> InlineText(content(node), md, ty.titleLarge, true)
        MarkdownElementTypes.ATX_3, MarkdownElementTypes.ATX_4, MarkdownElementTypes.ATX_5, MarkdownElementTypes.ATX_6 ->
            InlineText(content(node), md, ty.titleMedium, true)
        MarkdownElementTypes.UNORDERED_LIST -> ListBlock(node, false, md, depth)
        MarkdownElementTypes.ORDERED_LIST -> ListBlock(node, true, md, depth)
        MarkdownElementTypes.BLOCK_QUOTE -> Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.fillMaxHeight().width(3.dp).clip(CircleShape).background(md.linkColor.copy(alpha = 0.5f)))
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Blocks(node, md, depth + 1) }
        }
        MarkdownElementTypes.CODE_FENCE -> CodeBlock(fenceBody(node, md.src), md)
        MarkdownElementTypes.CODE_BLOCK -> CodeBlock(node.getTextInNode(md.src).toString().trimEnd(), md)
        MarkdownElementTypes.HTML_BLOCK -> Unit // Raw HTML is never rendered.
        GFMElementTypes.TABLE -> CodeBlock(node.getTextInNode(md.src).toString(), md)
        else -> if (node.children.isNotEmpty()) Blocks(node, md, depth + 1)
    }
}

private fun fenceBody(node: ASTNode, src: String): String {
    val body = node.getTextInNode(src).toString().lines().drop(1)
    val last = body.lastOrNull()?.trim()
    val trimmed = if (last != null && (last.startsWith("```") || last.startsWith("~~~"))) body.dropLast(1) else body
    return trimmed.joinToString("\n")
}

@Composable
private fun InlineText(node: ASTNode, md: Md, style: TextStyle, bold: Boolean = false) {
    val text = remember(node, md.src) { buildAnnotatedString { inline(node, md) } }
    Text(text, style = if (bold) style.copy(fontWeight = FontWeight.Bold) else style)
}

private fun AnnotatedString.Builder.inline(node: ASTNode, md: Md) {
    for (c in node.children) {
        when (c.type) {
            MarkdownElementTypes.EMPH -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { inline(c, md) }
            MarkdownElementTypes.STRONG -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { inline(c, md) }
            GFMElementTypes.STRIKETHROUGH ->
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { inline(c, md) }
            MarkdownElementTypes.CODE_SPAN ->
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = md.inlineBg)) {
                    append(c.getTextInNode(md.src).toString().trim('`').trim())
                }
            MarkdownElementTypes.INLINE_LINK -> {
                val label = c.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }
                    ?.getTextInNode(md.src)?.toString()?.removePrefix("[")?.removeSuffix("]").orEmpty()
                val dest = c.children.firstOrNull { it.type == MarkdownElementTypes.LINK_DESTINATION }
                    ?.getTextInNode(md.src)?.toString().orEmpty()
                link(if (label.contains("![")) "[image]" else label, dest, md)
            }
            MarkdownElementTypes.AUTOLINK -> {
                val u = c.getTextInNode(md.src).toString().trim('<', '>')
                link(u, u, md)
            }
            GFMTokenTypes.GFM_AUTOLINK -> {
                val u = c.getTextInNode(md.src).toString()
                link(u, u, md)
            }
            MarkdownElementTypes.IMAGE -> {
                val altText = c.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }
                    ?.getTextInNode(md.src)?.toString()?.removePrefix("![")?.removeSuffix("]").orEmpty()
                    .ifEmpty { "image" }
                val dest = c.children.firstOrNull { it.type == MarkdownElementTypes.LINK_DESTINATION }
                    ?.getTextInNode(md.src)?.toString().orEmpty()
                val safeDest = safeHttpUrl(dest)
                val label = "🖼️ [$altText]"
                if (safeDest != null) {
                    val style = TextLinkStyles(SpanStyle(color = md.linkColor, textDecoration = TextDecoration.Underline))
                    withLink(LinkAnnotation.Clickable(tag = safeDest, styles = style) { md.onLink(safeDest) }) {
                        append(label)
                    }
                } else {
                    append(label)
                }
            }
            MarkdownTokenTypes.EMPH, MarkdownTokenTypes.BACKTICK, GFMTokenTypes.TILDE,
            MarkdownTokenTypes.HTML_TAG, MarkdownElementTypes.HTML_BLOCK -> Unit
            MarkdownTokenTypes.EOL -> append(' ')
            else -> if (c.children.isEmpty()) append(c.getTextInNode(md.src).toString()) else inline(c, md)
        }
    }
}

private fun AnnotatedString.Builder.link(label: String, dest: String, md: Md) {
    val safe = safeHttpUrl(dest)
    if (safe == null) { // Unsafe, relative or non-http link: plain text, not clickable.
        append(label)
        return
    }
    val style = TextLinkStyles(SpanStyle(color = md.linkColor, textDecoration = TextDecoration.Underline))
    withLink(LinkAnnotation.Clickable(tag = safe, styles = style) { md.onLink(safe) }) { append(label) }
}

@Composable
private fun ListBlock(node: ASTNode, ordered: Boolean, md: Md, depth: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        var n = 1
        node.children.filter { it.type == MarkdownElementTypes.LIST_ITEM }.forEach { item ->
            Row {
                Text(if (ordered) "${n++}." else "\u2022", Modifier.width(24.dp), color = md.muted)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Blocks(item, md, depth + 1)
                }
            }
        }
    }
}

@Composable
private fun CodeBlock(code: String, md: Md) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = md.blockBg,
        border = BorderStroke(1.dp, md.inlineBg),
    ) {
        Box(Modifier.horizontalScroll(rememberScrollState()).padding(14.dp)) {
            Text(
                code,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                softWrap = false,
            )
        }
    }
}
