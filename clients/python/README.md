# MiniSQL Python Client

Thin Python SDK that talks to the Java `GatewayServer`.

## Generate stubs

```bash
python -m grpc_tools.protoc \
  -I ../../minisql-client/src/main/proto \
  --python_out=minisql_client \
  --grpc_python_out=minisql_client \
  gateway.proto
```

Stubs are gitignored; regenerate whenever `gateway.proto` changes.

## Usage

```python
from minisql_client import MiniSQLClient

with MiniSQLClient("localhost:9090") as client:
    client.execute("INSERT INTO users (user_id, username) VALUES (1, 'alice')")
    for row in client.fetchall("SELECT * FROM users WHERE user_id = 1"):
        print(row)
```

## Run tests

```bash
pip install -e .[dev]
pytest
```

Tests start an in-process Java gateway via `maven -pl minisql-client exec:java` — run
`mvn -pl minisql-client install -DskipTests` in the project root first.
