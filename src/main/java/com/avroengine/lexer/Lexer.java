package com.avroengine.lexer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.avroengine.lexer.TokenType.*;

/** Handwritten scanner: SQL text -&gt; list of {@link Token}, terminated by EOF. */
public final class Lexer {

    private static final Map<String, TokenType> KEYWORDS = Map.of(
            "SELECT", SELECT, "FROM", FROM, "WHERE", WHERE,
            "AND", AND, "OR", OR, "NOT", NOT,
            "TRUE", TRUE, "FALSE", FALSE, "NULL", NULL, "IS", IS);

    private final String src;
    private final int n;
    private int i = 0;

    public Lexer(String src) {
        this.src = src;
        this.n = src.length();
    }

    public static List<Token> tokenize(String src) {
        return new Lexer(src).scanAll();
    }

    private List<Token> scanAll() {
        List<Token> tokens = new ArrayList<>();
        while (true) {
            skipWhitespace();
            if (i >= n) {
                tokens.add(new Token(EOF, "", n));
                return tokens;
            }
            tokens.add(scanToken());
        }
    }

    private void skipWhitespace() {
        while (i < n && Character.isWhitespace(src.charAt(i))) {
            i++;
        }
    }

    private Token scanToken() {
        char c = src.charAt(i);
        if (Character.isDigit(c)) {
            return scanNumber();
        }
        if (Character.isLetter(c) || c == '_') {
            return scanIdentifierOrKeyword();
        }
        if (c == '\'') {
            return scanString();
        }
        return scanOperator();
    }

    private Token scanNumber() {
        int start = i;
        while (i < n && Character.isDigit(src.charAt(i))) {
            i++;
        }
        TokenType type = INTEGER_LITERAL;
        if (i < n && src.charAt(i) == '.' && i + 1 < n && Character.isDigit(src.charAt(i + 1))) {
            type = DECIMAL_LITERAL;
            i++; // consume '.'
            while (i < n && Character.isDigit(src.charAt(i))) {
                i++;
            }
        }
        return new Token(type, src.substring(start, i), start);
    }

    private Token scanIdentifierOrKeyword() {
        int start = i;
        i++;
        while (i < n && (Character.isLetterOrDigit(src.charAt(i)) || src.charAt(i) == '_')) {
            i++;
        }
        String lexeme = src.substring(start, i);
        TokenType keyword = KEYWORDS.get(lexeme.toUpperCase());
        return new Token(keyword != null ? keyword : IDENTIFIER, lexeme, start);
    }

    private Token scanString() {
        int start = i;
        i++; // consume opening '
        while (true) {
            if (i >= n) {
                throw new LexException("Unterminated string literal", start);
            }
            char c = src.charAt(i);
            if (c == '\'') {
                if (i + 1 < n && src.charAt(i + 1) == '\'') {
                    i += 2; // escaped '' -> literal '
                    continue;
                }
                i++; // closing quote
                break;
            }
            i++;
        }
        return new Token(STRING_LITERAL, src.substring(start, i), start);
    }

    private Token scanOperator() {
        int start = i;
        char c = src.charAt(i);
        switch (c) {
            case '=' -> {
                i++;
                return new Token(EQ, "=", start);
            }
            case '!' -> {
                if (peekIs('=')) {
                    i += 2;
                    return new Token(NEQ, "!=", start);
                }
                throw new LexException("Unexpected character '!'", start);
            }
            case '<' -> {
                if (peekIs('=')) {
                    i += 2;
                    return new Token(LTE, "<=", start);
                }
                if (peekIs('>')) {
                    i += 2;
                    return new Token(NEQ, "<>", start);
                }
                i++;
                return new Token(LT, "<", start);
            }
            case '>' -> {
                if (peekIs('=')) {
                    i += 2;
                    return new Token(GTE, ">=", start);
                }
                i++;
                return new Token(GT, ">", start);
            }
            case '+' -> {
                i++;
                return new Token(PLUS, "+", start);
            }
            case '-' -> {
                i++;
                return new Token(MINUS, "-", start);
            }
            case '*' -> {
                i++;
                return new Token(STAR, "*", start);
            }
            case '/' -> {
                i++;
                return new Token(SLASH, "/", start);
            }
            case ',' -> {
                i++;
                return new Token(COMMA, ",", start);
            }
            case '(' -> {
                i++;
                return new Token(LPAREN, "(", start);
            }
            case ')' -> {
                i++;
                return new Token(RPAREN, ")", start);
            }
            default -> throw new LexException("Unexpected character '" + c + "'", start);
        }
    }

    private boolean peekIs(char expected) {
        return i + 1 < n && src.charAt(i + 1) == expected;
    }
}
