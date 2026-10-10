package com.avroengine.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractiveShellTest {

    @TempDir
    Path tmp;

    private String runShell(String input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        new InteractiveShell(new BufferedReader(new StringReader(input)), out).run();
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private Path writeCsv() throws IOException {
        Path file = tmp.resolve("people.csv");
        Files.writeString(file,
                "id,name,age\r\n"
                        + "INT,STRING,INT\r\n"
                        + "\"1\",\"Dana\",\"30\"\r\n"
                        + "\"2\",\"Omer\",\"45\"\r\n");
        return file;
    }

    @Test
    void welcomeIsShownAndExitEndsSession() throws IOException {
        String output = runShell("exit\n");
        assertTrue(output.contains("How it works, in 3 steps"));
        assertTrue(output.contains("Goodbye!"));
    }

    @Test
    void loadsFileAndRunsQuery() throws IOException {
        Path csv = writeCsv();
        String output = runShell("load \"" + csv + "\"\nSELECT name FROM people WHERE age > 40;\nexit\n");
        assertTrue(output.contains("Loaded table 'people' with 3 columns"));
        assertTrue(output.contains("Omer"));
        assertTrue(output.contains("(1 row)"));
    }

    @Test
    void loadWithCustomTableName() throws IOException {
        Path csv = writeCsv();
        String output = runShell("load " + csv + " as staff\nSELECT id FROM staff\n");
        assertTrue(output.contains("Loaded table 'staff'"));
        assertTrue(output.contains("(2 rows)"));
    }

    @Test
    void fieldAfterFromGetsHelpfulHint() throws IOException {
        Path csv = writeCsv();
        String output = runShell("load " + csv + "\nSELECT * FROM name\n");
        assertTrue(output.contains("Unknown table 'name'"));
        assertTrue(output.contains("Loaded tables: people"));
    }

    @Test
    void queryBeforeLoadExplainsWhatToDo() throws IOException {
        String output = runShell("SELECT * FROM x\n");
        assertTrue(output.contains("No tables are loaded yet"));
    }

    @Test
    void unknownCommandPointsToHelp() throws IOException {
        String output = runShell("hello\n");
        assertTrue(output.contains("Type 'help'"));
    }

    @Test
    void tableNameIsDerivedFromFileName() {
        assertEquals("my_data_2024", InteractiveShell.tableNameFor(Path.of("My Data-2024.avro")));
        assertEquals("t_2024_sales", InteractiveShell.tableNameFor(Path.of("2024 sales.avro")));
        assertEquals("employees", InteractiveShell.tableNameFor(Path.of("employees.avro")));
    }
}
