package com.avroengine.ast;

/** One expression in the SELECT list, with the text used as its output column header. */
public record SelectItem(Expr expr, String displayName) {
}
