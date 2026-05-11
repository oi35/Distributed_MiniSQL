package com.minisql.client.gateway;

import com.minisql.client.exception.MiniSQLClientException;
import com.minisql.client.gateway.proto.ExecuteRequest;
import com.minisql.client.gateway.proto.ExecuteResponse;
import com.minisql.client.gateway.proto.GatewayServiceGrpc;
import com.minisql.client.gateway.proto.NullValue;
import com.minisql.client.gateway.proto.PingRequest;
import com.minisql.client.gateway.proto.PingResponse;
import com.minisql.client.gateway.proto.QueryResult;
import com.minisql.client.gateway.proto.Row;
import com.minisql.client.gateway.proto.TypedValue;
import com.minisql.client.gateway.proto.UpdateResult;
import com.minisql.client.sql.SqlExecutor;
import com.minisql.client.sql.SqlResult;
import com.minisql.common.proto.ErrorCode;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

public class GatewayServiceImpl extends GatewayServiceGrpc.GatewayServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(GatewayServiceImpl.class);
    private static final String VERSION = "1.0-SNAPSHOT";

    private final SqlExecutor executor;

    public GatewayServiceImpl(SqlExecutor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public void execute(ExecuteRequest request, StreamObserver<ExecuteResponse> observer) {
        ExecuteResponse response;
        try {
            SqlResult result = executor.execute(request.getSql());
            response = toResponse(result);
        } catch (MiniSQLClientException e) {
            LOG.debug("execute failed: {}", e.getMessage());
            response = errorResponse(e.getErrorCode(), e.getMessage());
        } catch (RuntimeException e) {
            LOG.warn("unexpected error executing SQL", e);
            response = errorResponse(ErrorCode.ERROR_INTERNAL, e.getMessage());
        }
        observer.onNext(response);
        observer.onCompleted();
    }

    @Override
    public void ping(PingRequest request, StreamObserver<PingResponse> observer) {
        observer.onNext(PingResponse.newBuilder()
                .setVersion(VERSION)
                .setServerTimeMs(System.currentTimeMillis())
                .build());
        observer.onCompleted();
    }

    private static ExecuteResponse toResponse(SqlResult result) {
        ExecuteResponse.Builder builder = ExecuteResponse.newBuilder().setSuccess(true);
        if (result.getKind() == SqlResult.Kind.UPDATE) {
            builder.setUpdate(UpdateResult.newBuilder()
                    .setAffectedRows(result.getUpdateCount())
                    .build());
        } else {
            QueryResult.Builder query = QueryResult.newBuilder()
                    .addAllColumns(result.getColumns());
            for (Map<String, Object> row : result.getRows()) {
                Row.Builder rowBuilder = Row.newBuilder();
                for (String col : result.getColumns()) {
                    rowBuilder.addValues(toTypedValue(row.get(col)));
                }
                query.addRows(rowBuilder);
            }
            builder.setQuery(query);
        }
        return builder.build();
    }

    private static ExecuteResponse errorResponse(ErrorCode code, String message) {
        return ExecuteResponse.newBuilder()
                .setSuccess(false)
                .setErrorCode(code.name())
                .setErrorMessage(message == null ? "" : message)
                .build();
    }

    private static TypedValue toTypedValue(Object v) {
        if (v == null) {
            return TypedValue.newBuilder().setNullValue(NullValue.NULL_VALUE).build();
        }
        if (v instanceof Boolean) {
            return TypedValue.newBuilder().setBoolValue((Boolean) v).build();
        }
        if (v instanceof Number) {
            if (v instanceof Double || v instanceof Float) {
                return TypedValue.newBuilder().setDoubleValue(((Number) v).doubleValue()).build();
            }
            return TypedValue.newBuilder().setIntValue(((Number) v).longValue()).build();
        }
        return TypedValue.newBuilder().setStringValue(v.toString()).build();
    }
}
