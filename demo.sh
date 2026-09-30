#!/usr/bin/env bash
# Builds the engine, converts the sample Avro file to typed CSV, and runs a
# handful of queries that show off SELECT *, projection, WHERE precedence,
# IS NULL, and a type error being rejected.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

JAR=target/engine.jar
DEMO_DIR=demo
AVRO_FILE="$DEMO_DIR/employees.avro"
CSV_FILE="$DEMO_DIR/employees.csv"

MVN=./mvnw
if [ ! -x "$MVN" ]; then
    MVN=mvn
fi

echo "== Building (this also runs the full JUnit test suite) =="
"$MVN" -q clean package

mkdir -p "$DEMO_DIR"

run_jar() {
    java -jar "$JAR" "$@"
}

echo
echo "== Stage 1: generating sample Avro data (20 employees) =="
run_jar generate-sample "$AVRO_FILE"

echo
echo "== Stage 1: converting Avro -> typed CSV (temporary intermediate) =="
run_jar convert "$AVRO_FILE" "$CSV_FILE"

query() {
    local title="$1"
    local sql="$2"
    echo
    echo "== $title =="
    echo "SQL: $sql"
    run_jar query "employees=$CSV_FILE" "$sql"
}

query "1. SELECT * (all columns, all rows)" \
      "SELECT * FROM employees"

query "2. Projection (subset of columns)" \
      "SELECT id, name, dept FROM employees"

query "3. WHERE with AND/OR precedence -- (dept = 'Sales' AND salary > 6300) OR name = 'Alice Kim'" \
      "SELECT name, dept, salary FROM employees WHERE dept = 'Sales' AND salary > 6300 OR name = 'Alice Kim'"

query "4. IS NULL -- employees with no manager" \
      "SELECT id, name FROM employees WHERE manager_id IS NULL"

echo
echo "== 5. Type error being rejected with a clear message =="
echo "SQL: SELECT * FROM employees WHERE name = salary"
if run_jar query "employees=$CSV_FILE" "SELECT * FROM employees WHERE name = salary"; then
    echo "ERROR: expected this query to be rejected, but it succeeded" >&2
    exit 1
else
    echo "(rejected as expected -- see the error message above)"
fi

echo
echo "== Demo complete =="
