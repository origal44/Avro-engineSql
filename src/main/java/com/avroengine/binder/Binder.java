package com.avroengine.binder;

import com.avroengine.ast.ColumnRef;
import com.avroengine.ast.Expr;
import com.avroengine.ast.SelectItem;
import com.avroengine.ast.SelectStatement;
import com.avroengine.catalog.Catalog;
import com.avroengine.datasource.Column;
import com.avroengine.datasource.DataSource;
import com.avroengine.datasource.DataType;
import com.avroengine.datasource.Schema;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves a parsed {@link SelectStatement} against a {@link Catalog}:
 * looks up the table, resolves every column name, and type-checks every
 * expression, producing a {@link BoundQuery} the executor can run directly.
 */
public final class Binder {

    private Binder() {
    }

    public static BoundQuery bind(SelectStatement stmt, Catalog catalog) {
        DataSource source = catalog.lookup(stmt.tableName());
        if (source == null) {
            throw new BindException("Unknown table '" + stmt.tableName() + "'");
        }
        Schema schema = source.schema();
        TypeChecker checker = new TypeChecker(schema);

        Expr where = stmt.where();
        if (where != null) {
            DataType whereType = where.accept(checker);
            if (whereType != null && whereType != DataType.BOOLEAN) {
                throw new BindException("WHERE clause must be boolean, but has type " + whereType);
            }
        }

        List<SelectItem> projections;
        List<Column> outputColumns = new ArrayList<>();
        if (stmt.selectAll()) {
            projections = new ArrayList<>(schema.size());
            for (Column column : schema.columns()) {
                projections.add(new SelectItem(new ColumnRef(column.name()), column.name()));
                outputColumns.add(column);
            }
        } else {
            projections = stmt.items();
            for (SelectItem item : stmt.items()) {
                DataType type = item.expr().accept(checker);
                // A bare `SELECT NULL` has no inferable type; STRING is an arbitrary
                // but harmless fallback since it never fails a display-time cast.
                outputColumns.add(new Column(item.displayName(), type != null ? type : DataType.STRING));
            }
        }

        return new BoundQuery(source, new Schema(outputColumns), projections, where);
    }
}
