# Avro-engineSql

A small hand-built SQL query engine that reads Avro data and runs `SELECT ... FROM ... WHERE ...`
queries against it, with a handwritten lexer, parser, binder, and batch-based executor. No parser
generators, no SQL libraries.

## Pipeline

```
.avro file --(Stage 1: stub converter)--> typed .csv --(Stage 2: CsvDataSource)--> DataSource
                                                                                        |
SQL text --(Stage 3: Lexer)--> tokens --(Stage 4: Parser)--> AST --(Stage 5: Binder)--> BoundQuery
                                                                                        |
                                                                          Scan -> Filter -> Project
                                                                                        |
                                                                                 printed table
```

- **Stage 1 -- Avro -> typed CSV (temporary stub).** `com.avroengine.stub.avro.AvroToCsvConverter`
  reads a flat Avro file and writes a CSV where row 1 is column names and row 2 is column types
  (`INT`, `LONG`, `DOUBLE`, `STRING`, `BOOLEAN`). NULL is written as `\N`; every other value
  (including an empty string) is quoted, so `\N` and `""` are never ambiguous. This is the **only**
  package that imports `org.apache.avro.*`, so it can be deleted wholesale later.
- **Stage 2 -- core model + DataSource.** `Schema`/`Column`/`Row`/`Batch` and the
  `DataSource` interface live in `com.avroengine.datasource`, along with `CsvDataSource`, the CSV
  implementation. The SQL engine itself (lexer/parser/ast/binder/exec) never imports CSV or Avro
  code -- it only depends on `DataSource`.
- **Stage 3 -- Lexer.** `com.avroengine.lexer.Lexer` hand-scans SQL text into `Token`s.
- **Stage 4 -- Parser.** `com.avroengine.parser.Parser` is a recursive-descent parser; expression
  precedence is resolved with precedence-climbing (Pratt-style) loops.
- **Stage 5 -- Binder + execution.** `com.avroengine.binder.Binder` resolves table/column names
  and type-checks every expression. `com.avroengine.exec` runs the bound query as a pipeline of
  batch operators (`ScanOperator -> FilterOperator -> ProjectOperator`), evaluating expressions with
  SQL three-valued logic (`TRUE`/`FALSE`/`UNKNOWN`; `WHERE` keeps only `TRUE`).
- **Stage 6 -- CLI.** `com.avroengine.cli.Main`.

**Note for later:** the CSV layer (`stub.avro` + `CsvDataSource`) is a temporary stand-in for a
handwritten Avro decoder. Once that decoder implements `DataSource` directly, it drops in without
any change to the lexer, parser, binder, or executor -- that's the point of the `DataSource`
boundary.

## Design patterns used

- **Strategy** -- `DataSource` is the interface the engine core depends on; `CsvDataSource` (and
  later a handwritten Avro decoder) are interchangeable strategies behind it.
- **Iterator** -- every execution stage (`ScanOperator`, `FilterOperator`, `ProjectOperator`)
  implements `Operator extends Iterator<Batch>`, so stages compose without knowing how upstream
  batches are produced.
- **Visitor** -- `ExprVisitor<T>` is implemented three times over the same AST: `TypeChecker`
  (binding), `ExprEvaluator` (execution), and `AstPrinter` (error messages / display).

## Running it

Prerequisites: JDK 21. Maven is not required -- the repo includes a Maven Wrapper.

```bash
./mvnw test              # run the full JUnit test suite
./mvnw package            # build target/engine.jar
./demo.sh                 # build + run a full demo (see below)
```

On Windows without a POSIX shell, use `mvnw.cmd` instead of `./mvnw`.

### CLI

```bash
java -jar target/engine.jar convert <file.avro> <out.csv>

java -jar target/engine.jar query <table-name>=<file.csv> [<table2>=<file2.csv> ...] "<SQL>"

java -jar target/engine.jar generate-sample <out.avro>   # writes the demo dataset
```

Example:

```bash
java -jar target/engine.jar generate-sample demo/employees.avro
java -jar target/engine.jar convert demo/employees.avro demo/employees.csv
java -jar target/engine.jar query "employees=demo/employees.csv" \
    "SELECT id, name FROM employees WHERE manager_id IS NULL"
```

### Demo

`./demo.sh` builds the project (running all tests), generates the sample `employees` dataset
(20 rows, including NULLs and an empty string), converts it to CSV, and runs five queries that show
`SELECT *`, projection, `WHERE` with `AND`/`OR` precedence, `IS NULL`, and a type error being
rejected with a clear message.

## Tests

Every stage has JUnit 5 tests alongside its code: lexer edge cases (escaping, position reporting),
parser precedence (`a OR b AND c`, `NOT`, parentheses), binder type errors, three-valued NULL
semantics, and CSV escaping/round-tripping. Run them with `./mvnw test`.
