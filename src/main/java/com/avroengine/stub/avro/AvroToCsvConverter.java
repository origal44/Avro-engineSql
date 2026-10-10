package com.avroengine.stub.avro;

import com.avroengine.datasource.CsvFormat;
import com.avroengine.datasource.DataType;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * TEMPORARY STUB: converts a flat Avro file into the engine's typed CSV
 * format (row 1 = column names, row 2 = column types, data rows after).
 *
 * <p>This is the only class in the project allowed to import
 * {@code org.apache.avro.*}. It is isolated in the {@code stub.avro} package
 * so it can be deleted wholesale once a handwritten Avro decoder implements
 * {@code DataSource} directly.
 */
public final class AvroToCsvConverter {

    /** One resolved column of a flat Avro record schema. */
    private record FieldPlan(String name, DataType type, boolean nullable) {
    }

    public void convert(Path avroFile, Path csvOut) throws IOException {
        try (DataFileReader<GenericRecord> reader =
                     new DataFileReader<>(avroFile.toFile(), new GenericDatumReader<>())) {

            Schema avroSchema = reader.getSchema();
            if (avroSchema.getType() != Schema.Type.RECORD) {
                throw new UnsupportedAvroTypeException(
                        "Top-level Avro schema must be a record, was: " + avroSchema.getType());
            }

            List<FieldPlan> plan = planFields(avroSchema);

            Files.createDirectories(csvOut.toAbsolutePath().getParent() == null
                    ? Path.of(".") : csvOut.toAbsolutePath().getParent());

            try (BufferedWriter out = Files.newBufferedWriter(csvOut, StandardCharsets.UTF_8)) {
                CsvFormat.writeRecord(out, plan.stream().map(FieldPlan::name).toList());
                CsvFormat.writeRecord(out, plan.stream().map(f -> f.type().name()).toList());

                for (GenericRecord record : reader) {
                    List<String> row = new ArrayList<>(plan.size());
                    for (FieldPlan field : plan) {
                        row.add(formatValue(record.get(field.name()), field));
                    }
                    CsvFormat.writeRecord(out, row);
                }
            }
        }
    }

    private List<FieldPlan> planFields(Schema recordSchema) {
        List<FieldPlan> plan = new ArrayList<>();
        for (Schema.Field field : recordSchema.getFields()) {
            plan.add(planField(field));
        }
        return plan;
    }

    private FieldPlan planField(Schema.Field field) {
        Schema fieldSchema = field.schema();
        if (fieldSchema.getType() == Schema.Type.UNION) {
            List<Schema> branches = fieldSchema.getTypes();
            if (branches.size() != 2) {
                throw new UnsupportedAvroTypeException(
                        "Field '" + field.name() + "': only nullable unions [\"null\", T] are supported, "
                                + "got a union with " + branches.size() + " branches");
            }
            Schema nullBranch = null;
            Schema valueBranch = null;
            for (Schema branch : branches) {
                if (branch.getType() == Schema.Type.NULL) {
                    nullBranch = branch;
                } else {
                    valueBranch = branch;
                }
            }
            if (nullBranch == null || valueBranch == null) {
                throw new UnsupportedAvroTypeException(
                        "Field '" + field.name() + "': union must contain exactly one \"null\" branch "
                                + "and one value branch");
            }
            return new FieldPlan(field.name(), mapPrimitive(field.name(), valueBranch), true);
        }
        return new FieldPlan(field.name(), mapPrimitive(field.name(), fieldSchema), false);
    }

    private DataType mapPrimitive(String fieldName, Schema schema) {
        return switch (schema.getType()) {
            case INT -> DataType.INT;
            case LONG -> DataType.LONG;
            case FLOAT, DOUBLE -> DataType.DOUBLE;
            case STRING -> DataType.STRING;
            case BOOLEAN -> DataType.BOOLEAN;
            default -> throw new UnsupportedAvroTypeException(
                    "Field '" + fieldName + "': unsupported Avro type " + schema.getType()
                            + " (only flat primitive fields and nullable unions of them are supported)");
        };
    }

    private String formatValue(Object value, FieldPlan field) {
        if (value == null) {
            return CsvFormat.NULL_MARKER;
        }
        String text = switch (field.type()) {
            case INT, LONG, BOOLEAN -> value.toString();
            case DOUBLE -> (value instanceof Float f) ? Double.toString(f.doubleValue()) : value.toString();
            case STRING -> value.toString();
        };
        return CsvFormat.quote(text);
    }

    /** Convenience CLI-friendly entry point mirroring the `convert` subcommand. */
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("Usage: AvroToCsvConverter <in.avro> <out.csv>");
            System.exit(1);
        }
        new AvroToCsvConverter().convert(Path.of(args[0]), Path.of(args[1]));
    }
}
