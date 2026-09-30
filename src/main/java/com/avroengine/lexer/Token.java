package com.avroengine.lexer;

/**
 * A single scanned token. {@code lexeme} is the raw source text (for
 * STRING_LITERAL this includes the surrounding quotes with {@code ''}
 * escapes still in place; the parser unescapes it when building a Literal).
 * {@code position} is the 0-based offset of the first character in the
 * original SQL text.
 */
public record Token(TokenType type, String lexeme, int position) {
}
