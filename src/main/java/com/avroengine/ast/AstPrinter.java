package com.avroengine.ast;

/**
 * Renders an expression back to canonical SQL text (fully parenthesized).
 * Visitor pattern -- used for binder/type-error messages, independent of the
 * parser's raw source-substring display names.
 */
public final class AstPrinter implements ExprVisitor<String> {

    public static String print(Expr expr) {
        return expr.accept(new AstPrinter());
    }

    @Override
    public String visitBinary(BinaryExpr e) {
        return "(" + e.left().accept(this) + " " + symbol(e.op()) + " " + e.right().accept(this) + ")";
    }

    @Override
    public String visitUnary(UnaryExpr e) {
        String op = switch (e.op()) {
            case NOT -> "NOT ";
            case NEG -> "-";
        };
        return "(" + op + e.operand().accept(this) + ")";
    }

    @Override
    public String visitColumnRef(ColumnRef e) {
        return e.name();
    }

    @Override
    public String visitLiteral(Literal e) {
        Object v = e.value();
        if (v == null) {
            return "NULL";
        }
        if (v instanceof String s) {
            return "'" + s.replace("'", "''") + "'";
        }
        return v.toString();
    }

    @Override
    public String visitIsNull(IsNullExpr e) {
        return "(" + e.operand().accept(this) + (e.negated() ? " IS NOT NULL)" : " IS NULL)");
    }

    private static String symbol(BinaryOp op) {
        return switch (op) {
            case OR -> "OR";
            case AND -> "AND";
            case EQ -> "=";
            case NEQ -> "!=";
            case LT -> "<";
            case LTE -> "<=";
            case GT -> ">";
            case GTE -> ">=";
            case PLUS -> "+";
            case MINUS -> "-";
            case MUL -> "*";
            case DIV -> "/";
        };
    }
}
