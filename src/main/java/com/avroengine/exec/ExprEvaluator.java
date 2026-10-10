package com.avroengine.exec;

import com.avroengine.ast.BinaryExpr;
import com.avroengine.ast.BinaryOp;
import com.avroengine.ast.ColumnRef;
import com.avroengine.ast.Expr;
import com.avroengine.ast.ExprVisitor;
import com.avroengine.ast.IsNullExpr;
import com.avroengine.ast.Literal;
import com.avroengine.ast.UnaryExpr;
import com.avroengine.datasource.Row;
import com.avroengine.datasource.Schema;

/**
 * Visitor pattern: evaluates an {@link Expr} against one {@link Row}.
 * Booleans follow SQL three-valued logic: results are {@code Boolean.TRUE},
 * {@code Boolean.FALSE}, or {@code null} for UNKNOWN.
 *
 * <p>Not thread-safe: a single instance is reused row-by-row within one
 * operator, with the current row swapped in before each {@link #evaluate}.
 */
public final class ExprEvaluator implements ExprVisitor<Object> {

    private final Schema schema;
    private Row row;

    public ExprEvaluator(Schema schema) {
        this.schema = schema;
    }

    public Object evaluate(Expr expr, Row row) {
        this.row = row;
        return expr.accept(this);
    }

    @Override
    public Object visitBinary(BinaryExpr e) {
        return switch (e.op()) {
            case AND -> and((Boolean) e.left().accept(this), (Boolean) e.right().accept(this));
            case OR -> or((Boolean) e.left().accept(this), (Boolean) e.right().accept(this));
            case EQ, NEQ, LT, LTE, GT, GTE -> evalComparison(e.op(), e.left().accept(this), e.right().accept(this));
            case PLUS, MINUS, MUL, DIV -> evalArithmetic(e.op(), e.left().accept(this), e.right().accept(this));
        };
    }

    @Override
    public Object visitUnary(UnaryExpr e) {
        Object operand = e.operand().accept(this);
        return switch (e.op()) {
            case NOT -> not((Boolean) operand);
            case NEG -> negate(operand);
        };
    }

    @Override
    public Object visitColumnRef(ColumnRef e) {
        int idx = schema.indexOf(e.name());
        return row.get(idx);
    }

    @Override
    public Object visitLiteral(Literal e) {
        return e.value();
    }

    @Override
    public Object visitIsNull(IsNullExpr e) {
        Object value = e.operand().accept(this);
        return e.negated() ? value != null : value == null;
    }

    // --- three-valued boolean logic ---

    private static Boolean and(Boolean a, Boolean b) {
        if (Boolean.FALSE.equals(a) || Boolean.FALSE.equals(b)) {
            return Boolean.FALSE;
        }
        if (a == null || b == null) {
            return null;
        }
        return a && b;
    }

    private static Boolean or(Boolean a, Boolean b) {
        if (Boolean.TRUE.equals(a) || Boolean.TRUE.equals(b)) {
            return Boolean.TRUE;
        }
        if (a == null || b == null) {
            return null;
        }
        return a || b;
    }

    private static Boolean not(Boolean a) {
        return a == null ? null : !a;
    }

    // --- comparisons ---

    private static Boolean evalComparison(BinaryOp op, Object l, Object r) {
        if (l == null || r == null) {
            return null; // any comparison with NULL is UNKNOWN
        }
        int cmp;
        if (l instanceof Number ln && r instanceof Number rn) {
            cmp = Double.compare(ln.doubleValue(), rn.doubleValue());
        } else if (l instanceof String ls && r instanceof String rs) {
            cmp = ls.compareTo(rs);
        } else if (l instanceof Boolean lb && r instanceof Boolean rb) {
            cmp = lb.equals(rb) ? 0 : 1; // only EQ/NEQ reach here; the binder rejects boolean ordering
        } else {
            throw new IllegalStateException("Incomparable runtime values: " + l + ", " + r);
        }
        return switch (op) {
            case EQ -> cmp == 0;
            case NEQ -> cmp != 0;
            case LT -> cmp < 0;
            case LTE -> cmp <= 0;
            case GT -> cmp > 0;
            case GTE -> cmp >= 0;
            default -> throw new IllegalStateException("Not a comparison operator: " + op);
        };
    }

    // --- arithmetic (INT/LONG/DOUBLE promotion, matching TypeChecker.numericResult) ---

    private static Object evalArithmetic(BinaryOp op, Object l, Object r) {
        if (l == null || r == null) {
            return null;
        }
        if (l instanceof Double || r instanceof Double) {
            double a = ((Number) l).doubleValue();
            double b = ((Number) r).doubleValue();
            return switch (op) {
                case PLUS -> a + b;
                case MINUS -> a - b;
                case MUL -> a * b;
                case DIV -> a / b;
                default -> throw new IllegalStateException("Not an arithmetic operator: " + op);
            };
        }
        if (l instanceof Long || r instanceof Long) {
            long a = ((Number) l).longValue();
            long b = ((Number) r).longValue();
            return switch (op) {
                case PLUS -> a + b;
                case MINUS -> a - b;
                case MUL -> a * b;
                case DIV -> a / b;
                default -> throw new IllegalStateException("Not an arithmetic operator: " + op);
            };
        }
        int a = (Integer) l;
        int b = (Integer) r;
        return switch (op) {
            case PLUS -> a + b;
            case MINUS -> a - b;
            case MUL -> a * b;
            case DIV -> a / b;
            default -> throw new IllegalStateException("Not an arithmetic operator: " + op);
        };
    }

    private static Object negate(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Double d) {
            return -d;
        }
        if (v instanceof Long l) {
            return -l;
        }
        return -(Integer) v;
    }
}
