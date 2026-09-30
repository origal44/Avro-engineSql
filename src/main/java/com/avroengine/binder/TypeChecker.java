package com.avroengine.binder;

import com.avroengine.ast.AstPrinter;
import com.avroengine.ast.BinaryExpr;
import com.avroengine.ast.ColumnRef;
import com.avroengine.ast.Expr;
import com.avroengine.ast.ExprVisitor;
import com.avroengine.ast.IsNullExpr;
import com.avroengine.ast.Literal;
import com.avroengine.ast.UnaryExpr;
import com.avroengine.datasource.DataType;
import com.avroengine.datasource.Schema;

/**
 * Visitor pattern: walks an {@link Expr}, resolving {@link ColumnRef} names
 * against a {@link Schema} and inferring each node's type, rejecting type
 * mismatches. A {@code null} return means "unknown type" (the SQL NULL
 * literal), which is compatible with everything.
 */
final class TypeChecker implements ExprVisitor<DataType> {

    private final Schema schema;

    TypeChecker(Schema schema) {
        this.schema = schema;
    }

    @Override
    public DataType visitBinary(BinaryExpr e) {
        DataType left = e.left().accept(this);
        DataType right = e.right().accept(this);
        return switch (e.op()) {
            case AND, OR -> {
                requireBooleanish(left, e.left(), e);
                requireBooleanish(right, e.right(), e);
                yield DataType.BOOLEAN;
            }
            case EQ, NEQ -> {
                requireComparable(left, right, e);
                yield DataType.BOOLEAN;
            }
            case LT, LTE, GT, GTE -> {
                requireOrderable(left, right, e);
                yield DataType.BOOLEAN;
            }
            case PLUS, MINUS, MUL, DIV -> {
                requireNumeric(left, e.left(), e);
                requireNumeric(right, e.right(), e);
                yield numericResult(left, right);
            }
        };
    }

    @Override
    public DataType visitUnary(UnaryExpr e) {
        DataType operand = e.operand().accept(this);
        return switch (e.op()) {
            case NOT -> {
                requireBooleanish(operand, e.operand(), e);
                yield DataType.BOOLEAN;
            }
            case NEG -> {
                requireNumeric(operand, e.operand(), e);
                yield operand;
            }
        };
    }

    @Override
    public DataType visitColumnRef(ColumnRef e) {
        int idx = schema.indexOf(e.name());
        if (idx < 0) {
            throw new BindException("Unknown column '" + e.name() + "'");
        }
        return schema.column(idx).type();
    }

    @Override
    public DataType visitLiteral(Literal e) {
        Object v = e.value();
        if (v == null) {
            return null; // NULL literal: unknown type, compatible with everything
        }
        if (v instanceof Long) {
            return DataType.LONG;
        }
        if (v instanceof Double) {
            return DataType.DOUBLE;
        }
        if (v instanceof String) {
            return DataType.STRING;
        }
        if (v instanceof Boolean) {
            return DataType.BOOLEAN;
        }
        throw new IllegalStateException("Unexpected literal value type: " + v.getClass());
    }

    @Override
    public DataType visitIsNull(IsNullExpr e) {
        e.operand().accept(this); // any type may be tested for nullness
        return DataType.BOOLEAN; // IS [NOT] NULL always yields TRUE/FALSE, never UNKNOWN
    }

    private static boolean isNumeric(DataType t) {
        return t == DataType.INT || t == DataType.LONG || t == DataType.DOUBLE;
    }

    private void requireBooleanish(DataType type, Expr operand, Expr context) {
        if (type != null && type != DataType.BOOLEAN) {
            throw mismatch(context, "expected BOOLEAN but '" + AstPrinter.print(operand) + "' has type " + type);
        }
    }

    private void requireNumeric(DataType type, Expr operand, Expr context) {
        if (type != null && !isNumeric(type)) {
            throw mismatch(context, "expected a numeric type but '" + AstPrinter.print(operand) + "' has type " + type);
        }
    }

    private void requireComparable(DataType left, DataType right, BinaryExpr e) {
        if (left == null || right == null) {
            return; // comparisons with NULL are always allowed (result is UNKNOWN at runtime)
        }
        boolean ok = (isNumeric(left) && isNumeric(right)) || left == right;
        if (!ok) {
            throw mismatch(e, "cannot compare " + left + " with " + right);
        }
    }

    private void requireOrderable(DataType left, DataType right, BinaryExpr e) {
        if (left == null || right == null) {
            return;
        }
        boolean ok = (isNumeric(left) && isNumeric(right)) || (left == DataType.STRING && right == DataType.STRING);
        if (!ok) {
            throw mismatch(e, "cannot order " + left + " and " + right + " (only numeric-numeric or string-string comparisons are supported)");
        }
    }

    private DataType numericResult(DataType left, DataType right) {
        if (left == null && right == null) {
            return DataType.LONG; // both operands are NULL literals; arbitrary numeric fallback
        }
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        if (left == DataType.DOUBLE || right == DataType.DOUBLE) {
            return DataType.DOUBLE;
        }
        if (left == DataType.LONG || right == DataType.LONG) {
            return DataType.LONG;
        }
        return DataType.INT;
    }

    private BindException mismatch(Expr context, String detail) {
        return new BindException("Type error in `" + AstPrinter.print(context) + "`: " + detail);
    }
}
