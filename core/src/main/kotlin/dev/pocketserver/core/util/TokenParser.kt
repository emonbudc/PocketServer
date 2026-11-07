package dev.pocketserver.core.util

data class TokenParseResult(
    val tokens: List<String>,
    val error: String? = null,
) {
    val ok: Boolean get() = error == null
}

object TokenParser {
    fun parse(input: String): TokenParseResult {
        if (input.contains('\n') || input.contains('\r')) {
            return TokenParseResult(emptyList(), "Multi-line input is not supported")
        }
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var tokenStarted = false

        for (char in input) {
            when {
                quote != null -> {
                    if (char == quote) quote = null else current.append(char)
                }
                char == '\'' || char == '"' -> {
                    quote = char
                    tokenStarted = true
                }
                char.isWhitespace() -> {
                    if (tokenStarted) {
                        tokens += current.toString()
                        current.clear()
                        tokenStarted = false
                    }
                }
                else -> {
                    current.append(char)
                    tokenStarted = true
                }
            }
        }

        if (quote != null) return TokenParseResult(emptyList(), "Unterminated ${quote}quote")
        if (tokenStarted) tokens += current.toString()
        return TokenParseResult(tokens)
    }
}
