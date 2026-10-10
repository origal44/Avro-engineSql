package com.avroengine.lexer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.avroengine.lexer.TokenType.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LexerTest {

    private List<TokenType> types(String sql) {
        return Lexer.tokenize(sql).stream().map(Token::type).toList();
    }

    @Test
    void tokenizesSimpleSelect() {
        assertEquals(
                List.of(SELECT, STAR, FROM, IDENTIFIER, EOF),
                types("SELECT * FROM t"));
    }

    @Test
    void keywordsAreCaseInsensitive() {
        assertEquals(List.of(SELECT, STAR, FROM, IDENTIFIER, WHERE, IDENTIFIER, EOF),
                types("select * from t where x"));
        assertEquals(List.of(SELECT, STAR, FROM, IDENTIFIER, EOF),
                types("SeLeCt * FrOm t"));
    }

    @Test
    void identifierKeepsOriginalCaseInLexeme() {
        List<Token> tokens = Lexer.tokenize("SeLeCt");
        assertEquals("SeLeCt", tokens.get(0).lexeme());
        assertEquals(SELECT, tokens.get(0).type());
    }

    @Test
    void scansIntegerAndDecimalLiterals() {
        List<Token> tokens = Lexer.tokenize("42 3.14");
        assertEquals(INTEGER_LITERAL, tokens.get(0).type());
        assertEquals("42", tokens.get(0).lexeme());
        assertEquals(DECIMAL_LITERAL, tokens.get(1).type());
        assertEquals("3.14", tokens.get(1).lexeme());
    }

    @Test
    void scansStringLiteralsWithEscapedQuote() {
        List<Token> tokens = Lexer.tokenize("'it''s here'");
        assertEquals(STRING_LITERAL, tokens.get(0).type());
        assertEquals("'it''s here'", tokens.get(0).lexeme());
    }

    @Test
    void unterminatedStringReportsPosition() {
        LexException ex = assertThrows(LexException.class, () -> Lexer.tokenize("'abc"));
        assertEquals(0, ex.position());
    }

    @Test
    void scansAllOperators() {
        assertEquals(
                List.of(EQ, NEQ, NEQ, LT, LTE, GT, GTE, PLUS, MINUS, STAR, SLASH, COMMA, LPAREN, RPAREN, EOF),
                types("= != <> < <= > >= + - * / , ( )"));
    }

    @Test
    void reportsPositionOfEachToken() {
        List<Token> tokens = Lexer.tokenize("SELECT id");
        assertEquals(0, tokens.get(0).position());
        assertEquals(7, tokens.get(1).position());
    }

    @Test
    void unexpectedCharacterReportsPosition() {
        LexException ex = assertThrows(LexException.class, () -> Lexer.tokenize("SELECT # FROM t"));
        assertEquals(7, ex.position());
    }

    @Test
    void selectStarIsDistinctFromMultiplyByContextNotLexer() {
        // The lexer just emits STAR either way; the parser disambiguates by position.
        assertEquals(List.of(SELECT, STAR, FROM, IDENTIFIER, EOF), types("SELECT * FROM t"));
        assertEquals(List.of(SELECT, IDENTIFIER, STAR, IDENTIFIER, FROM, IDENTIFIER, EOF),
                types("SELECT a * b FROM t"));
    }
}
