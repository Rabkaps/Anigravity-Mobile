package com.hambalapps.agymobile.ui.chat

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.hambalapps.agymobile.ui.theme.M3ExpressiveShape
import com.hambalapps.agymobile.ui.theme.MonoCodeStyle
import kotlinx.coroutines.delay

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
                    Text(
                        text = buildInlineStyled(block.text, codeBg, codeFg),
                        style = style,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = buildInlineStyled(block.text, codeBg, codeFg),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                is MarkdownBlock.CodeFence -> {
                    CodeFenceCard(language = block.language, code = block.code)
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
                style = MonoCodeStyle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
            )
        }
    }
}

private fun buildInlineStyled(
    input: String,
    inlineCodeBg: androidx.compose.ui.graphics.Color,
    inlineCodeFg: androidx.compose.ui.graphics.Color
): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < input.length) {
        when {
            input.startsWith("**", i) -> {
                val end = input.indexOf("**", i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(input.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(input[i++])
                }
            }
            input.startsWith("`", i) -> {
                val end = input.indexOf('`', i + 1)
                if (end > i + 1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = inlineCodeBg,
                            color = inlineCodeFg
                        )
                    ) {
                        append(" ${input.substring(i + 1, end)} ")
                    }
                    i = end + 1
                } else {
                    append(input[i++])
                }
            }
            input.startsWith("*", i) -> {
                val end = input.indexOf('*', i + 1)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(input.substring(i + 1, end))
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
