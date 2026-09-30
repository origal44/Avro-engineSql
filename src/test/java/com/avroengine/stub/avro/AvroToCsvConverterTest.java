package com.avroengine.stub.avro;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvroToCsvConverterTest {

    @TempDir
    Path tmp;

    @Test
    void convertsFlatRecordWithAllPrimitiveTypesAndNulls() throws IOException {
        Schema schema = TestAvroFiles.record("Emp",
                TestAvroFiles.field("id", Schema.Type.INT, false),
                TestAvroFiles.field("bignum", Schema.Type.LONG, false),
                TestAvroFiles.field("salary", Schema.Type.DOUBLE, false),
                TestAvroFiles.field("name", Schema.Type.STRING, false),
                TestAvroFiles.field("active", Schema.Type.BOOLEAN, false),
                TestAvroFiles.field("manager", Schema.Type.STRING, true));

        GenericRecord r1 = TestAvroFiles.newRecord(schema);
        r1.put("id", 1);
        r1.put("bignum", 100L);
        r1.put("salary", 5000.5);
        r1.put("name", "Alice");
        r1.put("active", true);
        r1.put("manager", null);

        GenericRecord r2 = TestAvroFiles.newRecord(schema);
        r2.put("id", 2);
        r2.put("bignum", 200L);
        r2.put("salary", 6000.0);
        r2.put("name", "Bob");
        r2.put("active", false);
        r2.put("manager", "Alice");

        Path avroFile = tmp.resolve("emp.avro");
        TestAvroFiles.write(avroFile, schema, List.of(r1, r2));

        Path csvFile = tmp.resolve("emp.csv");
        new AvroToCsvConverter().convert(avroFile, csvFile);

        List<String> lines = Files.readAllLines(csvFile, StandardCharsets.UTF_8);
        assertEquals("id,bignum,salary,name,active,manager", lines.get(0));
        assertEquals("INT,LONG,DOUBLE,STRING,BOOLEAN,STRING", lines.get(1));
        assertTrue(lines.get(2).endsWith(",\\N")); // r1 has null manager
        assertTrue(lines.get(3).endsWith(",\"Alice\"")); // r2's manager is a real string
    }

    @Test
    void writesEmptyStringAsQuotedEmptyAndNullAsBackslashN() throws IOException {
        Schema schema = TestAvroFiles.record("Row",
                TestAvroFiles.field("s", Schema.Type.STRING, true));

        GenericRecord empty = TestAvroFiles.newRecord(schema);
        empty.put("s", "");
        GenericRecord isNull = TestAvroFiles.newRecord(schema);
        isNull.put("s", null);

        Path avroFile = tmp.resolve("r.avro");
        TestAvroFiles.write(avroFile, schema, List.of(empty, isNull));

        Path csvFile = tmp.resolve("r.csv");
        new AvroToCsvConverter().convert(avroFile, csvFile);

        List<String> lines = Files.readAllLines(csvFile, StandardCharsets.UTF_8);
        assertEquals("\"\"", lines.get(2));
        assertEquals("\\N", lines.get(3));
    }

    @Test
    void escapesCommasQuotesAndNewlinesAndRoundTrips() throws IOException {
        Schema schema = TestAvroFiles.record("Row",
                TestAvroFiles.field("s", Schema.Type.STRING, false));

        GenericRecord rec = TestAvroFiles.newRecord(schema);
        rec.put("s", "a,b\"c\nd");

        Path avroFile = tmp.resolve("r.avro");
        TestAvroFiles.write(avroFile, schema, List.of(rec));

        Path csvFile = tmp.resolve("r.csv");
        new AvroToCsvConverter().convert(avroFile, csvFile);

        try (BufferedReader reader = Files.newBufferedReader(csvFile, StandardCharsets.UTF_8)) {
            com.avroengine.datasource.CsvFormat.readRecord(reader); // header
            com.avroengine.datasource.CsvFormat.readRecord(reader); // types
            List<String> dataRow = com.avroengine.datasource.CsvFormat.readRecord(reader);
            assertEquals("a,b\"c\nd", dataRow.get(0));
        }
    }

    @Test
    void rejectsNestedRecordField() throws IOException {
        Schema inner = TestAvroFiles.record("Inner", TestAvroFiles.field("x", Schema.Type.INT, false));
        Schema outer = TestAvroFiles.record("Outer",
                new Schema.Field("nested", inner, null, (Object) null));

        Path avroFile = tmp.resolve("nested.avro");
        TestAvroFiles.write(avroFile, outer, List.of());

        AvroToCsvConverter converter = new AvroToCsvConverter();
        Path csvFile = tmp.resolve("nested.csv");
        UnsupportedAvroTypeException ex = assertThrows(UnsupportedAvroTypeException.class,
                () -> converter.convert(avroFile, csvFile));
        assertTrue(ex.getMessage().contains("nested"));
    }

    @Test
    void rejectsUnionWithMoreThanTwoBranches() throws IOException {
        Schema union = Schema.createUnion(List.of(
                Schema.create(Schema.Type.NULL),
                Schema.create(Schema.Type.INT),
                Schema.create(Schema.Type.STRING)));
        Schema outer = TestAvroFiles.record("Outer",
                new Schema.Field("weird", union, null, (Object) null));

        Path avroFile = tmp.resolve("weird.avro");
        TestAvroFiles.write(avroFile, outer, List.of());

        AvroToCsvConverter converter = new AvroToCsvConverter();
        Path csvFile = tmp.resolve("weird.csv");
        assertThrows(UnsupportedAvroTypeException.class, () -> converter.convert(avroFile, csvFile));
    }
}
