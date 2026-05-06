"""MiniSQL Python client SDK.

Thin wrapper over the gRPC gateway exposed by the Java ``GatewayServer``.
"""

from __future__ import annotations

# grpc_tools.protoc emits ``import gateway_pb2`` at the top of the generated
# ``_pb2_grpc`` module, assuming both files sit directly on sys.path. When we
# ship them inside the ``minisql_client`` package, that absolute import fails.
# Pre-registering gateway_pb2 under its bare module name lets the generated
# code import it without post-editing the generated file.
import sys as _sys
from . import gateway_pb2 as _gateway_pb2

_sys.modules.setdefault("gateway_pb2", _gateway_pb2)

from contextlib import contextmanager
from dataclasses import dataclass
from typing import Any, Iterator, List, Optional

import grpc

from . import gateway_pb2 as pb
from . import gateway_pb2_grpc as pb_grpc


class MiniSQLError(RuntimeError):
    """Raised when the gateway reports an error or the RPC fails."""

    def __init__(self, message: str, error_code: str = ""):
        super().__init__(message)
        self.error_code = error_code


@dataclass
class UpdateResult:
    affected_rows: int


@dataclass
class QueryResult:
    columns: List[str]
    rows: List[dict]


class MiniSQLClient:
    """Blocking client. Use as a context manager to ensure the channel is closed."""

    def __init__(
        self,
        target: str,
        *,
        channel: Optional[grpc.Channel] = None,
        timeout: Optional[float] = None,
    ):
        self._owns_channel = channel is None
        self._channel = channel or grpc.insecure_channel(target)
        self._stub = pb_grpc.GatewayServiceStub(self._channel)
        self._timeout = timeout

    # -- lifecycle --------------------------------------------------------

    def close(self) -> None:
        if self._owns_channel:
            self._channel.close()

    def __enter__(self) -> "MiniSQLClient":
        return self

    def __exit__(self, exc_type, exc, tb) -> None:
        self.close()

    # -- public API -------------------------------------------------------

    def ping(self) -> pb.PingResponse:
        return self._stub.Ping(pb.PingRequest(), timeout=self._timeout)

    def execute(self, sql: str):
        """Execute any SQL. Returns :class:`UpdateResult` or :class:`QueryResult`."""
        try:
            response = self._stub.Execute(pb.ExecuteRequest(sql=sql), timeout=self._timeout)
        except grpc.RpcError as e:
            raise MiniSQLError(f"RPC failure: {e}") from e
        if not response.success:
            raise MiniSQLError(response.error_message, response.error_code)
        which = response.WhichOneof("result")
        if which == "update":
            return UpdateResult(affected_rows=response.update.affected_rows)
        if which == "query":
            return _query_result(response.query)
        raise MiniSQLError("gateway returned no result payload")

    def fetchall(self, sql: str) -> List[dict]:
        """Execute ``sql`` expecting a query and return all rows as dicts."""
        result = self.execute(sql)
        if not isinstance(result, QueryResult):
            raise MiniSQLError(f"{sql!r} did not return a result set")
        return result.rows

    def execute_update(self, sql: str) -> int:
        result = self.execute(sql)
        if not isinstance(result, UpdateResult):
            raise MiniSQLError(f"{sql!r} did not return an update count")
        return result.affected_rows


def _query_result(query: pb.QueryResult) -> QueryResult:
    columns = list(query.columns)
    rows = []
    for proto_row in query.rows:
        row = {}
        for col, value in zip(columns, proto_row.values):
            row[col] = _unwrap(value)
        rows.append(row)
    return QueryResult(columns=columns, rows=rows)


def _unwrap(value: pb.TypedValue) -> Any:
    which = value.WhichOneof("value")
    if which is None or which == "null_value":
        return None
    if which == "int_value":
        return value.int_value
    if which == "double_value":
        return value.double_value
    if which == "string_value":
        return value.string_value
    if which == "bool_value":
        return value.bool_value
    raise MiniSQLError(f"unknown TypedValue kind: {which}")


__all__ = [
    "MiniSQLClient",
    "MiniSQLError",
    "QueryResult",
    "UpdateResult",
]
