package com.avroengine.ast;

/** Visitor over the Expr hierarchy (Visitor pattern) -- used by binding, evaluation, and printing. */
public interface ExprVisitor<T> {
    T visitBinary(BinaryExpr e);

    T visitUnary(UnaryExpr e);

    T visitColumnRef(ColumnRef e);

    T visitLiteral(Literal e);

    T visitIsNull(IsNullExpr e);
}
