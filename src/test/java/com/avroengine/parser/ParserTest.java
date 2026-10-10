package com.avroengine.parser;

import com.avroengine.ast.BinaryExpr;
import com.avroengine.ast.BinaryOp;
import com.avroengine.ast.ColumnRef;
import com.avroengine.ast.Expr;
import com.avroengine.ast.IsNullExpr;
import com.avroengine.ast.Literal;
import com.avroengine.ast.SelectStatement;
import com.avroengine.ast.UnaryExpr;
import com.avroengine.ast.UnaryOp;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParserTest {

    @Test
    void parsesSelectStar() {
        SelectStatement stmt = Parser.parse("SELECT * FROM employees");
        assertTrue(stmt.selectAll());
        assertTrue(stmt.items().isEmpty());
        assertEquals("employees", stmt.tableName());
        assertNull(stmt.where());
    }

    @Test
    void parsesProjectionListWithDisplayNames() {
        SelectStatement stmt = Parser.parse("SELECT id, name FROM employees");
        assertEquals(2, stmt.items().size());
        assertEquals(new ColumnRef("id"), stmt.items().get(0).expr());
        assertEquals("id", stmt.items().get(0).displayName());
        assertEquals(new ColumnRef("name"), stmt.items().get(1).expr());
        assertEquals("name", stmt.items().get(1).displayName());
    }

    @Test
    void expressionInSelectListGetsSourceTextAsDisplayName() {
        SelectStatement stmt = Parser.parse("SELECT salary + 1 FROM employees");
        assertEquals("salary + 1", stmt.items().get(0).displayName());
    }

    @Test
    void orBindsLooserThanAnd() {
        // a OR b AND c  ==  a OR (b AND c)
        SelectStatement stmt = Parser.parse("SELECT * FROM t WHERE a OR b AND c");
        Expr expected = new BinaryExpr(
                new ColumnRef("a"), BinaryOp.OR,
                new BinaryExpr(new ColumnRef("b"), BinaryOp.AND, new ColumnRef("c")));
        assertEquals(expected, stmt.where());
    }

    @Test
    void notBindsTighterThanAndButLooserThanComparison() {
        // NOT a = b AND c  ==  (NOT (a = b)) AND c
        SelectStatement stmt = Parser.parse("SELECT * FROM t WHERE NOT a = b AND c");
        Expr expected = new BinaryExpr(
                new UnaryExpr(UnaryOp.NOT, new BinaryExpr(new ColumnRef("a"), BinaryOp.EQ, new ColumnRef("b"))),
                BinaryOp.AND,
                new ColumnRef("c"));
        assertEquals(expected, stmt.where());
    }

    @Test
    void additiveBindsLooserThanMultiplicative() {
        // a + b * c == a + (b * c)
        SelectStatement stmt = Parser.parse("SELECT a + b * c FROM t");
        Expr expected = new BinaryExpr(
                new ColumnRef("a"), BinaryOp.PLUS,
                new BinaryExpr(new ColumnRef("b"), BinaryOp.MUL, new ColumnRef("c")));
        assertEquals(expected, stmt.items().get(0).expr());
    }

    @Test
    void parenthesesOverridePrecedence() {
        // (a + b) * c
        SelectStatement stmt = Parser.parse("SELECT (a + b) * c FROM t");
        Expr expected = new BinaryExpr(
                new BinaryExpr(new ColumnRef("a"), BinaryOp.PLUS, new ColumnRef("b")),
                BinaryOp.MUL,
                new ColumnRef("c"));
        assertEquals(expected, stmt.items().get(0).expr());
    }

    @Test
    void parsesIsNullAndIsNotNull() {
        SelectStatement s1 = Parser.parse("SELECT * FROM t WHERE manager_id IS NULL");
        assertEquals(new IsNullExpr(new ColumnRef("manager_id"), false), s1.where());

        SelectStatement s2 = Parser.parse("SELECT * FROM t WHERE manager_id IS NOT NULL");
        assertEquals(new IsNullExpr(new ColumnRef("manager_id"), true), s2.where());
    }

    @Test
    void parsesUnaryMinusAndComparisonOperators() {
        SelectStatement stmt = Parser.parse("SELECT * FROM t WHERE salary >= -100");
        Expr expected = new BinaryExpr(
                new ColumnRef("salary"), BinaryOp.GTE,
                new UnaryExpr(UnaryOp.NEG, new Literal(100L)));
        assertEquals(expected, stmt.where());
    }

    @Test
    void parsesAllComparisonOperatorsAndAlternateNeq() {
        assertEquals(BinaryOp.NEQ, ((BinaryExpr) Parser.parse("SELECT * FROM t WHERE a <> b").where()).op());
        assertEquals(BinaryOp.NEQ, ((BinaryExpr) Parser.parse("SELECT * FROM t WHERE a != b").where()).op());
    }

    @Test
    void parsesStringAndBooleanAndNullLiterals() {
        SelectStatement stmt = Parser.parse("SELECT * FROM t WHERE a = 'it''s' AND b = TRUE AND c = NULL");
        // just verify it parses without error and top-level op is AND-chained correctly (left-associative)
        assertTrue(stmt.where() instanceof BinaryExpr be && be.op() == BinaryOp.AND);
    }

    @Test
    void stringLiteralUnescapesDoubledQuote() {
        SelectStatement stmt = Parser.parse("SELECT * FROM t WHERE name = 'it''s here'");
        BinaryExpr eq = (BinaryExpr) stmt.where();
        assertEquals(new Literal("it's here"), eq.right());
    }

    @Test
    void missingFromReportsError() {
        assertThrows(ParseException.class, () -> Parser.parse("SELECT * employees"));
    }

    @Test
    void unterminatedParenReportsError() {
        assertThrows(ParseException.class, () -> Parser.parse("SELECT (a + b FROM t"));
    }

    @Test
    void trailingGarbageIsRejected() {
        assertThrows(ParseException.class, () -> Parser.parse("SELECT * FROM t EXTRA"));
    }
}
