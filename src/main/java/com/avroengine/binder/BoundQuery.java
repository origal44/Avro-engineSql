package com.avroengine.binder;

import com.avroengine.ast.Expr;
import com.avroengine.ast.SelectItem;
import com.avroengine.datasource.DataSource;
import com.avroengine.datasource.Schema;

import java.util.List;

/**
 * A {@code SelectStatement} after name resolution and type-checking:
 * ready to execute against {@code source} without any further lookups.
 */
public record BoundQuery(DataSource source, Schema outputSchema, List<SelectItem> projections, Expr where) {
}
