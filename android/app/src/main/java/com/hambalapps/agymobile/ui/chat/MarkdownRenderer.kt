package com.hambalapps.agymobile.ui.chat

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.hambalapps.agymobile.ui.theme.M3ExpressiveShape
import com.hambalapps.agymobile.ui.theme.MonoCodeStyle
import kotlinx.coroutines.delay

private const val LRI = "\u2066"
private const val PDI = "\u2069"

internal fun detectTextDirection(text: String): TextDirection {
    for (ch in text) {
        if (ch.isWhitespace() || ch in "#*-`[]>._~|/\\:()[]{}\"'0123456789") continue
        when (Character.getDirectionality(ch)) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> return TextDirection.Rtl
            Character.DIRECTIONALITY_LEFT_TO_RIGHT -> return TextDirection.Ltr
        }
    }
    return TextDirection.Content
}

private sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class CodeFence(val language: String, val code: String) : MarkdownBlock
}

private fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val lines = markdown.lines()
    val blocks = mutableListOf<MarkdownBlock>()
    val proseBuffer = mutableListOf<String>()
    val codeBuffer = mutableListOf<String>()
    var inCode = false
    var codeLang = ""

    fun flushProse() {
        val joined = proseBuffer.joinToString("\n").trim()
        if (joined.isNotEmpty()) blocks.add(MarkdownBlock.Paragraph(joined))
        proseBuffer.clear()
    }

    for (line in lines) {
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```")) {
            if (!inCode) {
                flushProse()
                inCode = true
                codeLang = trimmed.removePrefix("```").trim()
                codeBuffer.clear()
            } else {
                blocks.add(MarkdownBlock.CodeFence(codeLang, codeBuffer.joinToString("\n")))
                inCode = false
                codeLang = ""
                codeBuffer.clear()
            }
            continue
        }

        if (inCode) {
            codeBuffer.add(line)
        } else if (trimmed.startsWith("#")) {
            val hashes = trimmed.takeWhile { it == '#' }.length
            if (hashes in 1..4 && trimmed.length > hashes && trimmed[hashes] == ' ') {
                flushProse()
                blocks.add(MarkdownBlock.Heading(hashes, trimmed.drop(hashes + 1)))
            } else {
                proseBuffer.add(line)
            }
        } else if (trimmed.isEmpty()) {
            flushProse()
        } else {
            proseBuffer.add(line)
        }
    }

    if (inCode) {
        blocks.add(MarkdownBlock.CodeFence(codeLang, codeBuffer.joinToString("\n")))
    } else {
        flushProse()
    }
    return blocks
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val codeFg = MaterialTheme.colorScheme.primary
    val linkColor = MaterialTheme.colorScheme.primary

    SelectionContainer {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            blocks.forEach { block ->
                when (block) {
                    is MarkdownBlock.Heading -> {
                        val style = when (block.level) {
                            1 -> MaterialTheme.typography.titleLarge
                            2 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.labelLarge
                        }
                        val dir = remember(block.text) { detectTextDirection(block.text) }
                        val styled = remember(block.text, codeBg, codeFg, linkColor) {
                            buildInlineStyled(block.text, codeBg, codeFg, linkColor)
                        }
                        Text(
                            text = styled,
                            style = style.copy(textDirection = dir),
                            textAlign = TextAlign.Start,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    is MarkdownBlock.Paragraph -> {
                        val dir = remember(block.text) { detectTextDirection(block.text) }
                        val styled = remember(block.text, codeBg, codeFg, linkColor) {
                            buildInlineStyled(block.text, codeBg, codeFg, linkColor)
                        }
                        Text(
                            text = styled,
                            style = MaterialTheme.typography.bodyLarge.copy(textDirection = dir),
                            textAlign = TextAlign.Start,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    is MarkdownBlock.CodeFence -> {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            CodeFenceCard(language = block.language, code = block.code)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeFenceCard(language: String, code: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = M3ExpressiveShape.Large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "code" }.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = "Copy code",
                        modifier = Modifier.size(16.dp),
                        tint = if (copied) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = code,
                style = MonoCodeStyle.copy(textDirection = TextDirection.Ltr),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
            )
        }
    }
}

private fun AnnotatedString.Builder.appendLink(
    label: String,
    url: String,
    linkColor: Color
) {
    val isBareUrl = label == url || label.startsWith("http://") || label.startsWith("https://")
    if (isBareUrl) append(LRI)
    withLink(
        LinkAnnotation.Url(
            url = url,
            styles = TextLinkStyles(
                style = SpanStyle(
                    color = linkColor,
                    textDecoration = TextDecoration.Underline
                )
            )
        )
    ) {
        append(label)
    }
    if (isBareUrl) append(PDI)
}

private fun consumeBareUrl(input: String, start: Int): String? {
    if (!input.startsWith("https://", start) && !input.startsWith("http://", start)) return null
    var end = start
    while (end < input.length) {
        val ch = input[end]
        if (ch.isWhitespace() || ch == '<' || ch == '>' || ch == '"' || ch == '\'') break
        end++
    }
    var raw = input.substring(start, end)
    while (raw.isNotEmpty()) {
        val last = raw.last()
        val isTrailingPunct = last in ".,;:!?" ||
            (last == ')' && raw.count { it == '(' } < raw.count { it == ')' })
        if (!isTrailingPunct) break
        raw = raw.dropLast(1)
    }
    return raw.takeIf { it.length > 8 }
}

internal fun linkifyPlainText(input: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < input.length) {
        val bareUrl = consumeBareUrl(input, i)
        if (bareUrl != null) {
            appendLink(bareUrl, bareUrl, linkColor)
            i += bareUrl.length
        } else {
            append(input[i++])
        }
    }
}

private fun buildInlineStyled(
    input: String,
    inlineCodeBg: Color,
    inlineCodeFg: Color,
    linkColor: Color
): AnnotatedString = buildAnnotatedString {
    appendInlineStyled(input, inlineCodeBg, inlineCodeFg, linkColor)
}

private fun AnnotatedString.Builder.appendInlineStyled(
    input: String,
    inlineCodeBg: Color,
    inlineCodeFg: Color,
    linkColor: Color
) {
    var i = 0
    while (i < input.length) {
        when {
            input[i] == '[' -> {
                val closeBracket = input.indexOf(']', i + 1)
                if (closeBracket > i + 1 &&
                    closeBracket + 1 < input.length &&
                    input[closeBracket + 1] == '('
                ) {
                    val closeParen = input.indexOf(')', closeBracket + 2)
                    if (closeParen > closeBracket + 2) {
                        val label = input.substring(i + 1, closeBracket)
                        val url = input.substring(closeBracket + 2, closeParen).trim()
                        if (url.startsWith("https://") || url.startsWith("http://")) {
                            appendLink(label, url, linkColor)
                            i = closeParen + 1
                            continue
                        }
                    }
                }
                append(input[i++])
            }
            input.startsWith("https://", i) || input.startsWith("http://", i) -> {
                val url = consumeBareUrl(input, i)
                if (url != null) {
                    appendLink(url, url, linkColor)
                    i += url.length
                } else {
                    append(input[i++])
                }
            }
            input.startsWith("**", i) -> {
                val end = input.indexOf("**", i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        appendInlineStyled(input.substring(i + 2, end), inlineCodeBg, inlineCodeFg, linkColor)
                    }
                    i = end + 2
                } else {
                    append(input[i++])
                }
            }
            input.startsWith("`", i) -> {
                val end = input.indexOf('`', i + 1)
                if (end > i + 1) {
                    append(LRI)
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = inlineCodeBg,
                            color = inlineCodeFg
                        )
                    ) {
                        append(" ${input.substring(i + 1, end)} ")
                    }
                    append(PDI)
                    i = end + 1
                } else {
                    append(input[i++])
                }
            }
            !input.startsWith("**", i) && input.startsWith("*", i) -> {
                val end = input.indexOf('*', i + 1)
                val lineBreak = input.indexOf('\n', i + 1)
                if (end > i + 1 && (lineBreak == -1 || end < lineBreak)) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        appendInlineStyled(input.substring(i + 1, end), inlineCodeBg, inlineCodeFg, linkColor)
                    }
                    i = end + 1
                } else {
                    append(input[i++])
                }
            }
            else -> append(input[i++])
        }
    }
}
