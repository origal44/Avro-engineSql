package com.avroengine.exec;

import com.avroengine.ast.BinaryExpr;
import com.avroengine.ast.BinaryOp;
import com.avroengine.ast.ColumnRef;
import com.avroengine.ast.IsNullExpr;
import com.avroengine.ast.Literal;
import com.avroengine.ast.UnaryExpr;
import com.avroengine.ast.UnaryOp;
import com.avroengine.datasource.Column;
import com.avroengine.datasource.DataType;
import com.avroengine.datasource.Row;
import com.avroengine.datasource.Schema;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ExprEvaluatorTest {

    private final Schema schema = new Schema(List.of(
            new Column("a", DataType.LONG),
            new Column("b", DataType.LONG),
            new Column("s", DataType.STRING),
            new Column("flag", DataType.BOOLEAN)));

    private Object eval(com.avroengine.ast.Expr expr, Object... values) {
        return new ExprEvaluator(schema).evaluate(expr, new Row(values));
    }

    @Test
    void threeValuedAnd() {
        Literal t = new Literal(Boolean.TRUE), f = new Literal(Boolean.FALSE), nul = new Literal(null);
        assertEquals(Boolean.TRUE, eval(new BinaryExpr(t, BinaryOp.AND, t)));
        assertEquals(Boolean.FALSE, eval(new BinaryExpr(t, BinaryOp.AND, f)));
        assertEquals(Boolean.FALSE, eval(new BinaryExpr(f, BinaryOp.AND, nul))); // FALSE dominates
        assertNull(eval(new BinaryExpr(t, BinaryOp.AND, nul)));
    }

    @Test
    void threeValuedOr() {
        Literal t = new Literal(Boolean.TRUE), f = new Literal(Boolean.FALSE), nul = new Literal(null);
        assertEquals(Boolean.TRUE, eval(new BinaryExpr(t, BinaryOp.OR, nul))); // TRUE dominates
        assertEquals(Boolean.FALSE, eval(new BinaryExpr(f, BinaryOp.OR, f)));
        assertNull(eval(new BinaryExpr(f, BinaryOp.OR, nul)));
    }

    @Test
    void comparisonWithNullIsUnknown() {
        assertNull(eval(new BinaryExpr(new ColumnRef("a"), BinaryOp.EQ, new Literal(null)), 1L, 2L, "x", true));
    }

    @Test
    void isNullNeverReturnsUnknown() {
        assertEquals(Boolean.TRUE, eval(new IsNullExpr(new ColumnRef("a"), false), (Object) null, 2L, "x", true));
        assertEquals(Boolean.FALSE, eval(new IsNullExpr(new ColumnRef("a"), false), 1L, 2L, "x", true));
        assertEquals(Boolean.TRUE, eval(new IsNullExpr(new ColumnRef("a"), true), 1L, 2L, "x", true));
    }

    @Test
    void arithmeticPromotesToDouble() {
        Object result = eval(new BinaryExpr(new ColumnRef("a"), BinaryOp.PLUS, new Literal(1.5)), 10L, 2L, "x", true);
        assertEquals(11.5, result);
    }

    @Test
    void integerDivisionTruncates() {
        Object result = eval(new BinaryExpr(new Literal(7L), BinaryOp.DIV, new Literal(2L)), 1L, 2L, "x", true);
        assertEquals(3L, result);
    }

    @Test
    void unaryNegAndNot() {
        assertEquals(-5L, eval(new UnaryExpr(UnaryOp.NEG, new ColumnRef("a")), 5L, 2L, "x", true));
        assertEquals(Boolean.FALSE, eval(new UnaryExpr(UnaryOp.NOT, new ColumnRef("flag")), 1L, 2L, "x", true));
    }

    @Test
    void stringComparison() {
        assertEquals(Boolean.TRUE,
                eval(new BinaryExpr(new ColumnRef("s"), BinaryOp.EQ, new Literal("x")), 1L, 2L, "x", true));
        assertEquals(Boolean.FALSE,
                eval(new BinaryExpr(new ColumnRef("s"), BinaryOp.EQ, new Literal("y")), 1L, 2L, "x", true));
    }
}
