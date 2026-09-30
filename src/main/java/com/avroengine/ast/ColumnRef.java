package com.avroengine.ast;

public record ColumnRef(String name) implements Expr {
    @Override
    public <T> T accept(ExprVisitor<T> visitor) {
        return visitor.visitColumnRef(this);
    }
}
