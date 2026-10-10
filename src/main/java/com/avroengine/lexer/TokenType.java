package com.avroengine.lexer;

public enum TokenType {
    // Keywords
    SELECT, FROM, WHERE, AND, OR, NOT, TRUE, FALSE, NULL, IS,

    // Literals / identifiers
    IDENTIFIER, INTEGER_LITERAL, DECIMAL_LITERAL, STRING_LITERAL,

    // Operators / punctuation
    EQ, NEQ, LT, LTE, GT, GTE,
    PLUS, MINUS, STAR, SLASH,
    COMMA, LPAREN, RPAREN,

    EOF
}
