package com.avroengine.datasource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * A {@link DataSource} backed by the engine's typed CSV format (row 1 =
 * names, row 2 = types). This is the temporary stand-in for a real Avro
 * decoder -- the engine core only ever sees it through {@link DataSource}.
 */
public final class CsvDataSource implements DataSource {

    private final Path file;
    private final Schema schema;

    public CsvDataSource(Path file) {
        this.file = file;
        this.schema = readSchema(file);
    }

    @Override
    public Schema schema() {
        return schema;
    }

    @Override
    public Iterator<Batch> batches(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive, was " + batchSize);
        }
        return new CsvBatchIterator(batchSize);
    }

    private static Schema readSchema(Path file) {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<String> names = CsvFormat.readRecord(reader);
            List<String> types = CsvFormat.readRecord(reader);
            if (names == null || types == null) {
                throw new CsvDataSourceException("CSV file '" + file + "' is missing the name/type header rows");
            }
            if (names.size() != types.size()) {
                throw new CsvDataSourceException("CSV file '" + file + "': header has " + names.size()
                        + " names but " + types.size() + " types");
            }
            List<Column> columns = new ArrayList<>(names.size());
            for (int i = 0; i < names.size(); i++) {
                columns.add(new Column(names.get(i), parseType(file, names.get(i), types.get(i))));
            }
            return new Schema(columns);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read CSV schema from " + file, e);
        }
    }

    private static DataType parseType(Path file, String columnName, String typeName) {
        try {
            return DataType.valueOf(typeName.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CsvDataSourceException("CSV file '" + file + "': column '" + columnName
                    + "' has unknown type '" + typeName + "'");
        }
    }

    private Object convert(String raw, DataType type, String columnName) {
        if (raw == null) {
            return null;
        }
        try {
            return switch (type) {
                case INT -> Integer.parseInt(raw);
                case LONG -> Long.parseLong(raw);
                case DOUBLE -> Double.parseDouble(raw);
                case STRING -> raw;
                case BOOLEAN -> Boolean.parseBoolean(raw);
            };
        } catch (NumberFormatException e) {
            throw new CsvDataSourceException("CSV file '" + file + "': value '" + raw
                    + "' in column '" + columnName + "' is not a valid " + type);
        }
    }

    private final class CsvBatchIterator implements Iterator<Batch> {
        private final int batchSize;
        private final BufferedReader reader;
        private Batch pending;
        private boolean closed = false;

        CsvBatchIterator(int batchSize) {
            this.batchSize = batchSize;
            try {
                this.reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
                CsvFormat.readRecord(reader); // skip names row
                CsvFormat.readRecord(reader); // skip types row
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to open CSV file " + file, e);
            }
            advance();
        }

        private void advance() {
            if (closed) {
                pending = null;
                return;
            }
            List<Row> rows = new ArrayList<>(batchSize);
            try {
                for (int i = 0; i < batchSize; i++) {
                    List<String> fields = CsvFormat.readRecord(reader);
                    if (fields == null) {
                        break;
                    }
                    if (fields.size() != schema.size()) {
                        throw new CsvDataSourceException("CSV file '" + file + "': expected " + schema.size()
                                + " fields but row has " + fields.size());
                    }
                    Object[] values = new Object[schema.size()];
                    for (int c = 0; c < schema.size(); c++) {
                        Column column = schema.column(c);
                        values[c] = convert(fields.get(c), column.type(), column.name());
                    }
                    rows.add(new Row(values));
                }
            } catch (IOException e) {
                closeQuietly();
                throw new UncheckedIOException("Failed to read CSV data from " + file, e);
            }
            if (rows.isEmpty()) {
                closeQuietly();
                pending = null;
            } else {
                pending = new Batch(schema, rows);
            }
        }

        private void closeQuietly() {
            closed = true;
            try {
                reader.close();
            } catch (IOException ignored) {
                // best-effort close
            }
        }

        @Override
        public boolean hasNext() {
            return pending != null;
        }

        @Override
        public Batch next() {
            if (pending == null) {
                throw new NoSuchElementException();
            }
            Batch result = pending;
            advance();
            return result;
        }
    }
}
