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
 * Generates the demo dataset (~20 employees) used by demo.sh: a flat record
 * with primitive fields and a nullable manager_id, including NULLs and an
 * empty string, to exercise every case the Avro-&gt;CSV stub handles.
 */
public final class SampleEmployeesGenerator {

    private static final Schema SCHEMA = buildSchema();

    private record Emp(int id, String name, String dept, double salary, Integer managerId) {
    }

    private static final List<Emp> ROWS = List.of(
            new Emp(1, "Alice Kim", "Engineering", 12500.00, null),
            new Emp(2, "Bob Chen", "Engineering", 9800.00, 1),
            new Emp(3, "Carol Diaz", "Engineering", 9600.50, 1),
            new Emp(4, "Dave Novak", "Engineering", 8700.00, 2),
            new Emp(5, "Erin Walsh", "Sales", 7600.25, null),
            new Emp(6, "Frank Ito", "Sales", 6400.00, 5),
            new Emp(7, "Grace Lin", "Sales", 6550.75, 5),
            new Emp(8, "Heidi Suarez", "Marketing", 7100.00, null),
            new Emp(9, "Ivan Petrov", "Marketing", 5900.00, 8),
            new Emp(10, "Judy Nakamura", "Marketing", 6050.00, 8),
            new Emp(11, "Kevin O'Brien", "Engineering", 8900.00, 2),
            new Emp(12, "Laura Silva", "Engineering", 9100.00, 2),
            new Emp(13, "Mallory Faber", "", 5000.00, null),
            new Emp(14, "Niaj Rahman", "Sales", 6200.00, 5),
            new Emp(15, "Olivia Brandt", "Sales", 6300.00, 5),
            new Emp(16, "Peggy Zhou", "Marketing", 5800.00, 8),
            new Emp(17, "Quentin Ross", "Engineering", 8300.00, 1),
            new Emp(18, "Ruth Ahlgren", "Engineering", 8600.00, 1),
            new Emp(19, "Sybil Costa", "Marketing", 5700.00, 8),
            new Emp(20, "Trent Ueda", "Sales", 6100.00, 5));

    private SampleEmployeesGenerator() {
    }

    public static void generate(Path outAvroFile) throws IOException {
        try (DataFileWriter<GenericRecord> writer = new DataFileWriter<>(new GenericDatumWriter<>(SCHEMA))) {
            writer.create(SCHEMA, outAvroFile.toFile());
            for (Emp e : ROWS) {
                GenericRecord record = new GenericData.Record(SCHEMA);
                record.put("id", e.id());
                record.put("name", e.name());
                record.put("dept", e.dept());
                record.put("salary", e.salary());
                record.put("manager_id", e.managerId());
                writer.append(record);
            }
        }
    }

    private static Schema buildSchema() {
        Schema nullableInt = Schema.createUnion(List.of(Schema.create(Schema.Type.NULL), Schema.create(Schema.Type.INT)));
        Schema schema = Schema.createRecord("Employee", null, "com.avroengine.demo", false);
        schema.setFields(List.of(
                new Schema.Field("id", Schema.create(Schema.Type.INT), null, (Object) null),
                new Schema.Field("name", Schema.create(Schema.Type.STRING), null, (Object) null),
                new Schema.Field("dept", Schema.create(Schema.Type.STRING), null, (Object) null),
                new Schema.Field("salary", Schema.create(Schema.Type.DOUBLE), null, (Object) null),
                new Schema.Field("manager_id", nullableInt, null, Schema.Field.NULL_DEFAULT_VALUE)));
        return schema;
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: SampleEmployeesGenerator <out.avro>");
            System.exit(1);
        }
        generate(Path.of(args[0]));
    }
}
