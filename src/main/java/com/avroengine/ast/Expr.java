package com.avroengine.ast;

/** Base type of the expression AST. Immutable; walked via {@link ExprVisitor}. */
public sealed interface Expr permits BinaryExpr, UnaryExpr, ColumnRef, Literal, IsNullExpr {
    <T> T accept(ExprVisitor<T> visitor);
}
