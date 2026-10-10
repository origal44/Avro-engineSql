package com.avroengine.ast;

import java.util.List;

/**
 * {@code SELECT (* | expr [, expr]*) FROM table [WHERE expr]}.
 * When {@code selectAll} is true, {@code items} is empty and the binder
 * expands it to all columns of the source table.
 */
public record SelectStatement(boolean selectAll, List<SelectItem> items, String tableName, Expr where) {
}
