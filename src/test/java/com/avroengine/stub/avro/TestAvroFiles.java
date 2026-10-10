package com.avroengine.stub.avro;

import org.apache.avro.Schema;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Small in-test builder for flat Avro files, used only by tests in this
 * package to exercise {@link AvroToCsvConverter} without checking in binary
 * fixture files.
 */
public final class TestAvroFiles {

    private TestAvroFiles() {
    }

    public static Schema.Field field(String name, Schema.Type type, boolean nullable) {
        Schema base = Schema.create(type);
        Schema fieldSchema = nullable
                ? Schema.createUnion(List.of(Schema.create(Schema.Type.NULL), base))
                : base;
        return new Schema.Field(name, fieldSchema, null, nullable ? Schema.Field.NULL_DEFAULT_VALUE : null);
    }

    public static Schema record(String name, Schema.Field... fields) {
        Schema schema = Schema.createRecord(name, null, "com.avroengine.test", false);
        schema.setFields(List.of(fields));
        return schema;
    }

    public static GenericRecord newRecord(Schema schema) {
        return new GenericData.Record(schema);
    }

    public static void write(Path file, Schema schema, List<GenericRecord> records) throws IOException {
        try (DataFileWriter<GenericRecord> writer = new DataFileWriter<>(new GenericDatumWriter<>(schema))) {
            writer.create(schema, file.toFile());
            for (GenericRecord record : records) {
                writer.append(record);
            }
        }
    }
}
