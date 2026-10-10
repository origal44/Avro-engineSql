package com.avroengine.binder;

import com.avroengine.catalog.Catalog;
import com.avroengine.datasource.Column;
import com.avroengine.datasource.CsvDataSource;
import com.avroengine.datasource.DataType;
import com.avroengine.parser.Parser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BinderTest {

    @TempDir
    Path tmp;

    private Catalog catalogWithEmployees() throws IOException {
        Path file = tmp.resolve("employees.csv");
        Files.writeString(file,
                "id,name,salary,active,manager\r\n"
                        + "INT,STRING,DOUBLE,BOOLEAN,STRING\r\n"
                        + "\"1\",\"Alice\",\"5000.0\",\"true\",\\N\r\n"
                        + "\"2\",\"Bob\",\"6000.0\",\"false\",\"Alice\"\r\n");
        Catalog catalog = new Catalog();
        catalog.register("employees", new CsvDataSource(file));
        return catalog;
    }

    @Test
    void bindsSelectStarToSourceSchema() throws IOException {
        Catalog catalog = catalogWithEmployees();
        BoundQuery q = Binder.bind(Parser.parse("SELECT * FROM employees"), catalog);
        assertEquals(5, q.outputSchema().size());
        assertEquals(new Column("id", DataType.INT), q.outputSchema().column(0));
    }

    @Test
    void bindsProjectionExpressionTypeAndName() throws IOException {
        Catalog catalog = catalogWithEmployees();
        BoundQuery q = Binder.bind(Parser.parse("SELECT salary + 1 FROM employees"), catalog);
        assertEquals(1, q.outputSchema().size());
        assertEquals("salary + 1", q.outputSchema().column(0).name());
        assertEquals(DataType.DOUBLE, q.outputSchema().column(0).type());
    }

    @Test
    void unknownTableIsRejected() throws IOException {
        Catalog catalog = catalogWithEmployees();
        BindException ex = assertThrows(BindException.class,
                () -> Binder.bind(Parser.parse("SELECT * FROM ghosts"), catalog));
        assertTrue(ex.getMessage().contains("ghosts"));
    }

    @Test
    void unknownColumnIsRejected() throws IOException {
        Catalog catalog = catalogWithEmployees();
        BindException ex = assertThrows(BindException.class,
                () -> Binder.bind(Parser.parse("SELECT nope FROM employees"), catalog));
        assertTrue(ex.getMessage().contains("nope"));
    }

    @Test
    void comparingStringToNumberIsRejectedWithClearMessage() throws IOException {
        Catalog catalog = catalogWithEmployees();
        BindException ex = assertThrows(BindException.class,
                () -> Binder.bind(Parser.parse("SELECT * FROM employees WHERE name = salary"), catalog));
        assertTrue(ex.getMessage().contains("STRING"));
        assertTrue(ex.getMessage().contains("DOUBLE"));
    }

    @Test
    void arithmeticOnStringIsRejected() throws IOException {
        Catalog catalog = catalogWithEmployees();
        assertThrows(BindException.class,
                () -> Binder.bind(Parser.parse("SELECT name + 1 FROM employees"), catalog));
    }

    @Test
    void whereClauseMustBeBoolean() throws IOException {
        Catalog catalog = catalogWithEmployees();
        assertThrows(BindException.class,
                () -> Binder.bind(Parser.parse("SELECT * FROM employees WHERE salary"), catalog));
    }

    @Test
    void comparingWithNullLiteralIsAlwaysAllowed() throws IOException {
        Catalog catalog = catalogWithEmployees();
        BoundQuery q = Binder.bind(Parser.parse("SELECT * FROM employees WHERE manager = NULL"), catalog);
        assertEquals(5, q.outputSchema().size());
    }
}
