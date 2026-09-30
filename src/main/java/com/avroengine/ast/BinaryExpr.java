package com.avroengine.ast;

public record BinaryExpr(Expr left, BinaryOp op, Expr right) implements Expr {
    @Override
    public <T> T accept(ExprVisitor<T> visitor) {
        return visitor.visitBinary(this);
    }
}
