#!/usr/bin/env bash
# Builds the engine and drives the interactive shell with a scripted session:
# loads the sample data, then runs queries that show off SELECT *, projection,
# WHERE precedence, IS NULL, and a type error being rejected.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

JAR=target/engine.jar

MVN=./mvnw
if [ ! -x "$MVN" ]; then
    MVN=mvn
fi

echo "== Building (this also runs the full JUnit test suite) =="
"$MVN" -q clean package

echo
echo "== Running a scripted interactive session =="
java -jar "$JAR" <<'EOF'
sample
SELECT * FROM employees
SELECT id, name, dept FROM employees
SELECT name, dept, salary FROM employees WHERE dept = 'Sales' AND salary > 6300 OR name = 'Alice Kim'
SELECT id, name FROM employees WHERE manager_id IS NULL
SELECT * FROM employees WHERE name = salary
exit
EOF

echo
echo "== Demo complete =="
