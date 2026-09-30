package com.avroengine.ast;

/** {@code operand IS NULL} (negated=false) or {@code operand IS NOT NULL} (negated=true). */
public record IsNullExpr(Expr operand, boolean negated) implements Expr {
    @Override
    public <T> T accept(ExprVisitor<T> visitor) {
        return visitor.visitIsNull(this);
    }
}
