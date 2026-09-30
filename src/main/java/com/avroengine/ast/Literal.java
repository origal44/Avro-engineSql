package com.avroengine.ast;

/**
 * A literal value. {@code value} is one of Integer, Long, Double, String,
 * Boolean, or {@code null} for the SQL NULL literal.
 */
public record Literal(Object value) implements Expr {
    @Override
    public <T> T accept(ExprVisitor<T> visitor) {
        return visitor.visitLiteral(this);
    }
}
