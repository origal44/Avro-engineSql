package com.avroengine.ast;

public record UnaryExpr(UnaryOp op, Expr operand) implements Expr {
    @Override
    public <T> T accept(ExprVisitor<T> visitor) {
        return visitor.visitUnary(this);
    }
}
