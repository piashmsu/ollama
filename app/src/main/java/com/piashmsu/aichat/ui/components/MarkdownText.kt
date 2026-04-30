package com.piashmsu.aichat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.piashmsu.aichat.ui.theme.MonoFont

/**
 * Lightweight markdown renderer that handles:
 *  - paragraphs
 *  - inline emphasis (`*` `_` `**` `__`), code spans `` ` ``, links `[txt](url)`
 *  - fenced code blocks with optional language tag (rendered as boxed CodeBlock with copy button)
 *  - bullet lists (`- ` / `* `) and numbered lists (`1.`)
 *
 * We avoid pulling Markwon into Compose (which is View-based) to keep the
 * implementation pure-Compose and themeable. This is intentionally simple but
 * covers what an LLM actually emits 95% of the time.
 */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (block in blocks) {
            when (block) {
                is MdBlock.Paragraph -> Text(
                    text = renderInline(block.text),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                is MdBlock.BulletList -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    block.items.forEach {
                        Row {
                            Text("•  ", color = MaterialTheme.colorScheme.primary)
                            Text(renderInline(it), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                is MdBlock.NumberedList -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    block.items.forEachIndexed { i, t ->
                        Row {
                            Text("${i + 1}.  ", color = MaterialTheme.colorScheme.primary)
                            Text(renderInline(t), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                is MdBlock.Code -> CodeBlock(language = block.language, code = block.code)
                is MdBlock.Heading -> Text(
                    text = renderInline(block.text),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineMedium
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun CodeBlock(language: String?, code: String) {
    val clip = LocalClipboardManager.current
    val highlighted = remember(language, code) { highlightCode(language, code) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = (language ?: "code").lowercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            IconButton(onClick = { clip.setText(AnnotatedString(code)) }) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Copy",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val scroll = rememberScrollState()
        Text(
            text = highlighted,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            fontFamily = MonoFont,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun renderInline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end == -1) { append(text.substring(i)); i = text.length }
                else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                }
            }
            text.startsWith("__", i) -> {
                val end = text.indexOf("__", i + 2)
                if (end == -1) { append(text.substring(i)); i = text.length }
                else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                }
            }
            text[i] == '*' || text[i] == '_' -> {
                val ch = text[i]
                val end = text.indexOf(ch, i + 1)
                if (end == -1) { append(text[i]); i++ }
                else {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end == -1) { append(text[i]); i++ }
                else {
                    withStyle(SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color(0x33000000),
                    )) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                }
            }
            text[i] == '[' -> {
                val labelEnd = text.indexOf(']', i + 1)
                val openParen = if (labelEnd != -1) text.getOrNull(labelEnd + 1) else null
                if (labelEnd == -1 || openParen != '(') { append(text[i]); i++ }
                else {
                    val close = text.indexOf(')', labelEnd + 2)
                    if (close == -1) { append(text[i]); i++ }
                    else {
                        val label = text.substring(i + 1, labelEnd)
                        withStyle(SpanStyle(
                            color = Color(0xFF80D8FF),
                            textDecoration = TextDecoration.Underline,
                        )) {
                            append(label)
                        }
                        i = close + 1
                    }
                }
            }
            else -> { append(text[i]); i++ }
        }
    }
}

private sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class BulletList(val items: List<String>) : MdBlock
    data class NumberedList(val items: List<String>) : MdBlock
    data class Code(val language: String?, val code: String) : MdBlock
}

private fun parseMarkdownBlocks(input: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val lines = input.lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("```") -> {
                val lang = trimmed.removePrefix("```").trim().ifBlank { null }
                val sb = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    sb.appendLine(lines[i]); i++
                }
                i++ // skip closing fence
                out += MdBlock.Code(language = lang, code = sb.toString().trimEnd())
            }
            trimmed.startsWith("#") -> {
                val level = trimmed.takeWhile { it == '#' }.length.coerceAtMost(6)
                out += MdBlock.Heading(level, trimmed.drop(level).trim())
                i++
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                val items = mutableListOf<String>()
                while (i < lines.size && (lines[i].trimStart().startsWith("- ") || lines[i].trimStart().startsWith("* "))) {
                    items += lines[i].trimStart().drop(2)
                    i++
                }
                out += MdBlock.BulletList(items)
            }
            trimmed.matches(Regex("^\\d+\\.\\s.*")) -> {
                val items = mutableListOf<String>()
                while (i < lines.size && lines[i].trimStart().matches(Regex("^\\d+\\.\\s.*"))) {
                    items += lines[i].trimStart().substringAfter(' ')
                    i++
                }
                out += MdBlock.NumberedList(items)
            }
            line.isBlank() -> i++
            else -> {
                val sb = StringBuilder()
                while (i < lines.size && lines[i].isNotBlank() &&
                    !lines[i].trimStart().startsWith("```") &&
                    !lines[i].trimStart().startsWith("#") &&
                    !lines[i].trimStart().startsWith("- ") &&
                    !lines[i].trimStart().startsWith("* ") &&
                    !lines[i].trimStart().matches(Regex("^\\d+\\.\\s.*"))) {
                    if (sb.isNotEmpty()) sb.append('\n')
                    sb.append(lines[i]); i++
                }
                out += MdBlock.Paragraph(sb.toString())
            }
        }
    }
    return out
}

// Tiny syntax highlighter — keywords + strings + numbers + comments. Good
// enough to look like ChatGPT/Gemini code boxes for popular languages.
private val KEYWORDS = mapOf(
    "kotlin" to setOf("fun", "val", "var", "return", "if", "else", "when", "class", "object", "interface", "import", "package", "for", "while", "do", "is", "as", "in", "true", "false", "null", "private", "public", "internal", "open", "override", "data", "suspend", "lateinit", "by", "this", "throw", "try", "catch", "finally", "sealed", "abstract", "companion"),
    "python" to setOf("def", "class", "return", "if", "elif", "else", "for", "while", "import", "from", "as", "try", "except", "finally", "with", "lambda", "True", "False", "None", "self", "yield", "in", "not", "and", "or", "pass", "raise", "global", "nonlocal", "async", "await"),
    "javascript" to setOf("function", "const", "let", "var", "return", "if", "else", "for", "while", "import", "export", "from", "as", "class", "extends", "new", "this", "true", "false", "null", "undefined", "try", "catch", "finally", "throw", "async", "await", "of", "in", "typeof", "instanceof"),
    "typescript" to setOf("function", "const", "let", "var", "return", "if", "else", "for", "while", "import", "export", "from", "as", "class", "extends", "new", "this", "true", "false", "null", "undefined", "interface", "type", "enum", "async", "await", "public", "private", "readonly", "implements"),
    "tsx" to setOf("function", "const", "let", "var", "return", "if", "else", "for", "while", "import", "export", "from", "as", "class", "extends", "new", "this", "true", "false", "null", "undefined", "interface", "type", "enum", "async", "await", "public", "private", "readonly"),
    "jsx" to setOf("function", "const", "let", "var", "return", "if", "else", "for", "while", "import", "export", "from", "as", "class", "extends", "new", "this", "true", "false", "null", "undefined"),
    "java" to setOf("public", "private", "protected", "class", "interface", "extends", "implements", "static", "final", "void", "if", "else", "for", "while", "return", "new", "this", "try", "catch", "finally", "throw", "throws", "true", "false", "null", "package", "import", "abstract", "synchronized"),
    "rust" to setOf("fn", "let", "mut", "const", "struct", "enum", "trait", "impl", "pub", "use", "mod", "if", "else", "match", "for", "while", "loop", "return", "self", "Self", "true", "false", "where", "ref", "as", "break", "continue", "async", "await"),
    "go" to setOf("func", "var", "const", "type", "struct", "interface", "map", "chan", "package", "import", "if", "else", "for", "range", "switch", "case", "default", "return", "go", "select", "defer", "true", "false", "nil"),
    "c" to setOf("int", "char", "float", "double", "void", "long", "short", "unsigned", "signed", "if", "else", "for", "while", "do", "switch", "case", "default", "return", "break", "continue", "struct", "union", "enum", "typedef", "static", "const", "extern", "sizeof"),
    "cpp" to setOf("int", "char", "float", "double", "void", "long", "short", "bool", "auto", "if", "else", "for", "while", "do", "switch", "case", "default", "return", "break", "continue", "struct", "class", "namespace", "template", "typename", "public", "private", "protected", "virtual", "override", "const", "static", "new", "delete", "true", "false", "nullptr", "this", "using"),
    "swift" to setOf("func", "let", "var", "if", "else", "for", "while", "switch", "case", "default", "return", "class", "struct", "enum", "protocol", "extension", "import", "guard", "in", "self", "Self", "true", "false", "nil", "throws", "throw", "try", "catch", "do", "is", "as"),
    "php" to setOf("function", "class", "if", "else", "elseif", "for", "foreach", "while", "do", "return", "echo", "print", "true", "false", "null", "public", "private", "protected", "static", "const", "use", "namespace", "new", "this", "abstract", "interface", "extends", "implements"),
    "ruby" to setOf("def", "end", "class", "module", "if", "elsif", "else", "unless", "while", "until", "for", "in", "do", "return", "yield", "true", "false", "nil", "self", "require", "include", "begin", "rescue", "ensure", "raise"),
    "sql" to setOf("select", "from", "where", "and", "or", "not", "in", "like", "between", "is", "null", "insert", "into", "values", "update", "set", "delete", "create", "table", "drop", "alter", "add", "primary", "key", "foreign", "references", "join", "left", "right", "inner", "outer", "on", "group", "by", "having", "order", "asc", "desc", "limit", "offset", "as", "distinct", "count", "sum", "avg", "min", "max", "case", "when", "then", "else", "end"),
    "json" to setOf("true", "false", "null"),
    "yaml" to setOf("true", "false", "null", "yes", "no", "on", "off"),
    "shell" to setOf("if", "then", "else", "fi", "for", "do", "done", "while", "case", "esac", "function", "return", "exit", "elif", "in", "until", "select", "trap"),
    "bash" to setOf("if", "then", "else", "fi", "for", "do", "done", "while", "case", "esac", "function", "return", "exit", "elif", "in", "until", "select", "trap", "local", "export", "source"),
    "html" to setOf(),
    "css" to setOf(),
    "xml" to setOf(),
    "markdown" to setOf(),
)

// Common aliases: "js" → "javascript", "ts" → "typescript", etc.
private val HASH_COMMENT_LANGS = setOf("python", "shell", "bash", "ruby", "yaml", "toml")

private fun resolveLang(lang: String?): String = when (lang?.lowercase()) {
    "kt", "kts" -> "kotlin"
    "py", "py3" -> "python"
    "js" -> "javascript"
    "ts" -> "typescript"
    "rs" -> "rust"
    "rb" -> "ruby"
    "yml" -> "yaml"
    "sh", "zsh" -> "bash"
    "c++", "cxx", "cc", "hpp", "h" -> "cpp"
    null, "" -> ""
    else -> lang.lowercase()
}

private fun highlightCode(language: String?, code: String): AnnotatedString {
    val lang = resolveLang(language)
    val keys = KEYWORDS[lang] ?: emptySet()
    return buildAnnotatedString {
        var i = 0
        while (i < code.length) {
            val c = code[i]
            // line / block comments
            if (c == '/' && i + 1 < code.length && code[i + 1] == '/') {
                val end = code.indexOf('\n', i).let { if (it == -1) code.length else it }
                withStyle(SpanStyle(color = Color(0xFF6E7A99))) { append(code.substring(i, end)) }
                i = end; continue
            }
            if (c == '#' && lang in HASH_COMMENT_LANGS) {
                val end = code.indexOf('\n', i).let { if (it == -1) code.length else it }
                withStyle(SpanStyle(color = Color(0xFF6E7A99))) { append(code.substring(i, end)) }
                i = end; continue
            }
            // string literals
            if (c == '"' || c == '\'') {
                val quote = c
                val sb = StringBuilder().append(c); i++
                while (i < code.length && code[i] != quote) {
                    if (code[i] == '\\' && i + 1 < code.length) { sb.append(code[i]); i++ }
                    sb.append(code[i]); i++
                }
                if (i < code.length) { sb.append(code[i]); i++ }
                withStyle(SpanStyle(color = Color(0xFFA5D6A7))) { append(sb.toString()) }
                continue
            }
            // numbers
            if (c.isDigit()) {
                val sb = StringBuilder()
                while (i < code.length && (code[i].isDigit() || code[i] == '.')) { sb.append(code[i]); i++ }
                withStyle(SpanStyle(color = Color(0xFFFFB74D))) { append(sb.toString()) }
                continue
            }
            // identifiers / keywords
            if (c.isLetter() || c == '_') {
                val sb = StringBuilder()
                while (i < code.length && (code[i].isLetterOrDigit() || code[i] == '_')) {
                    sb.append(code[i]); i++
                }
                val word = sb.toString()
                if (word in keys) {
                    withStyle(SpanStyle(color = Color(0xFF80D8FF), fontWeight = FontWeight.SemiBold)) { append(word) }
                } else {
                    append(word)
                }
                continue
            }
            append(c); i++
        }
    }
}
