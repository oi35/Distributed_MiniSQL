package com.minisql.client.schema;

import com.minisql.client.exception.MiniSQLClientException;
import com.minisql.common.proto.ErrorCode;
import com.minisql.common.proto.TableSchema;
import com.minisql.master.proto.ClientMasterServiceGrpc;
import com.minisql.master.proto.GetTableSchemaRequest;
import com.minisql.master.proto.GetTableSchemaResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class TableSchemaCache {

    private static final Logger LOG = LoggerFactory.getLogger(TableSchemaCache.class);

    private final ClientMasterServiceGrpc.ClientMasterServiceBlockingStub masterStub;
    private final Map<String, TableSchema> cache = new ConcurrentHashMap<>();

    public TableSchemaCache(ClientMasterServiceGrpc.ClientMasterServiceBlockingStub masterStub) {
        this.masterStub = Objects.requireNonNull(masterStub, "masterStub");
    }

    public TableSchema get(String tableName) {
        TableSchema cached = cache.get(tableName);
        if (cached != null) {
            return cached;
        }
        return load(tableName);
    }

    public void invalidate(String tableName) {
        cache.remove(tableName);
    }

    public void invalidateAll() {
        cache.clear();
    }

    private TableSchema load(String tableName) {
        GetTableSchemaRequest request = GetTableSchemaRequest.newBuilder()
                .setTableName(tableName)
                .build();
        GetTableSchemaResponse response;
        try {
            response = masterStub.getTableSchema(request);
        } catch (RuntimeException e) {
            throw new MiniSQLClientException(
                    "failed to fetch schema for " + tableName,
                    ErrorCode.ERROR_UNAVAILABLE, e);
        }
        if (!response.getSuccess()) {
            throw new MiniSQLClientException(
                    "master rejected schema request: " + response.getErrorMessage(),
                    response.getErrorCode());
        }
        TableSchema schema = response.getSchema();
        cache.put(tableName, schema);
        LOG.debug("loaded schema for {}, columns={}, pk={}",
                tableName, schema.getColumnsCount(), schema.getPrimaryKey());
        return schema;
    }
}
