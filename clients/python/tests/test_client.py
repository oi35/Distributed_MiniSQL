"""Unit tests for the Python client using a Python-side fake gateway."""

from concurrent import futures

import grpc
import pytest

from minisql_client import MiniSQLClient, MiniSQLError
from minisql_client import gateway_pb2 as pb
from minisql_client import gateway_pb2_grpc as pb_grpc


class FakeGateway(pb_grpc.GatewayServiceServicer):
    def __init__(self):
        self.last_sql = None
        self.update_response = None
        self.query_response = None
        self.error = None

    def Execute(self, request, context):
        self.last_sql = request.sql
        if self.error:
            return pb.ExecuteResponse(
                success=False,
                error_code=self.error[0],
                error_message=self.error[1],
            )
        if self.update_response is not None:
            return pb.ExecuteResponse(success=True, update=self.update_response)
        if self.query_response is not None:
            return pb.ExecuteResponse(success=True, query=self.query_response)
        return pb.ExecuteResponse(success=False, error_code="NO_STUB", error_message="no canned response")

    def Ping(self, request, context):
        return pb.PingResponse(version="test", server_time_ms=123)


@pytest.fixture
def gateway():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=2))
    fake = FakeGateway()
    pb_grpc.add_GatewayServiceServicer_to_server(fake, server)
    port = server.add_insecure_port("127.0.0.1:0")
    server.start()
    try:
        yield fake, f"127.0.0.1:{port}"
    finally:
        server.stop(grace=None)


def test_ping_returns_version(gateway):
    _fake, addr = gateway
    with MiniSQLClient(addr) as client:
        response = client.ping()
    assert response.version == "test"


def test_execute_update_returns_count(gateway):
    fake, addr = gateway
    fake.update_response = pb.UpdateResult(affected_rows=3)
    with MiniSQLClient(addr) as client:
        count = client.execute_update("DELETE FROM users")
    assert count == 3
    assert fake.last_sql == "DELETE FROM users"


def test_fetchall_decodes_typed_values(gateway):
    fake, addr = gateway
    fake.query_response = pb.QueryResult(
        columns=["id", "name", "score", "active", "note"],
        rows=[
            pb.Row(values=[
                pb.TypedValue(int_value=7),
                pb.TypedValue(string_value="alice"),
                pb.TypedValue(double_value=9.5),
                pb.TypedValue(bool_value=True),
                pb.TypedValue(null_value=pb.NULL_VALUE),
            ]),
        ],
    )
    with MiniSQLClient(addr) as client:
        rows = client.fetchall("SELECT * FROM users")
    assert rows == [{
        "id": 7,
        "name": "alice",
        "score": 9.5,
        "active": True,
        "note": None,
    }]


def test_error_raises_minisql_error(gateway):
    fake, addr = gateway
    fake.error = ("ERROR_TABLE_NOT_FOUND", "no such table: ghosts")
    with MiniSQLClient(addr) as client:
        with pytest.raises(MiniSQLError) as excinfo:
            client.execute("SELECT * FROM ghosts WHERE id = 1")
    assert excinfo.value.error_code == "ERROR_TABLE_NOT_FOUND"
    assert "ghosts" in str(excinfo.value)


def test_fetchall_rejects_update_result(gateway):
    fake, addr = gateway
    fake.update_response = pb.UpdateResult(affected_rows=1)
    with MiniSQLClient(addr) as client:
        with pytest.raises(MiniSQLError):
            client.fetchall("DELETE FROM users")
