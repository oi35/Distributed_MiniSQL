package com.minisql.client.route;

import com.google.protobuf.ByteString;
import com.minisql.client.exception.MiniSQLClientException;
import com.minisql.common.proto.ErrorCode;
import com.minisql.common.proto.RegionRouteTable;
import com.minisql.common.proto.RouteEntry;
import com.minisql.master.proto.ClientMasterServiceGrpc;
import com.minisql.master.proto.GetRouteTableRequest;
import com.minisql.master.proto.GetRouteTableResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class RouteCache {

    private static final Logger LOG = LoggerFactory.getLogger(RouteCache.class);

    private final ClientMasterServiceGrpc.ClientMasterServiceBlockingStub masterStub;
    private final Map<String, SortedRouteTable> cache = new ConcurrentHashMap<>();

    public RouteCache(ClientMasterServiceGrpc.ClientMasterServiceBlockingStub masterStub) {
        this.masterStub = Objects.requireNonNull(masterStub, "masterStub");
    }

    public RouteEntry lookup(String tableName, ByteString key) {
        SortedRouteTable table = getOrLoad(tableName);
        RouteEntry entry = table.findForKey(key);
        if (entry == null) {
            throw new MiniSQLClientException(
                    "no region covers the key in table " + tableName,
                    ErrorCode.ERROR_REGION_NOT_FOUND);
        }
        return entry;
    }

    public List<RouteEntry> lookupRange(String tableName, ByteString startKey, ByteString endKey) {
        SortedRouteTable table = getOrLoad(tableName);
        return table.findForRange(startKey, endKey);
    }

    public void invalidate(String tableName) {
        cache.remove(tableName);
    }

    public void invalidateAll() {
        cache.clear();
    }

    public RegionRouteTable refresh(String tableName) {
        return load(tableName).raw;
    }

    private SortedRouteTable getOrLoad(String tableName) {
        SortedRouteTable cached = cache.get(tableName);
        if (cached != null) {
            return cached;
        }
        return load(tableName);
    }

    private SortedRouteTable load(String tableName) {
        GetRouteTableRequest request = GetRouteTableRequest.newBuilder()
                .setTableName(tableName)
                .setCachedVersion(0)
                .build();
        GetRouteTableResponse response;
        try {
            response = masterStub.getRouteTable(request);
        } catch (RuntimeException e) {
            throw new MiniSQLClientException(
                    "failed to fetch route table for " + tableName,
                    ErrorCode.ERROR_UNAVAILABLE, e);
        }
        if (!response.getSuccess()) {
            throw new MiniSQLClientException(
                    "master rejected route table request: " + response.getErrorMessage(),
                    response.getErrorCode());
        }
        SortedRouteTable sorted = new SortedRouteTable(response.getRouteTable());
        cache.put(tableName, sorted);
        LOG.debug("loaded route table for {}, version={}, regions={}",
                tableName, sorted.raw.getVersion(), sorted.entries.size());
        return sorted;
    }

    static final class SortedRouteTable {
        private static final Comparator<ByteString> UNSIGNED = RouteCache::compareUnsigned;
        final RegionRouteTable raw;
        final List<RouteEntry> entries;

        SortedRouteTable(RegionRouteTable raw) {
            this.raw = raw;
            List<RouteEntry> sorted = new ArrayList<>(raw.getRoutesList());
            sorted.sort(Comparator.comparing(RouteEntry::getStartKey, UNSIGNED));
            this.entries = sorted;
        }

        RouteEntry findForKey(ByteString key) {
            for (RouteEntry entry : entries) {
                if (contains(entry, key)) {
                    return entry;
                }
            }
            return null;
        }

        List<RouteEntry> findForRange(ByteString start, ByteString end) {
            List<RouteEntry> hit = new ArrayList<>();
            for (RouteEntry entry : entries) {
                if (overlaps(entry, start, end)) {
                    hit.add(entry);
                }
            }
            return hit;
        }
    }

    private static boolean contains(RouteEntry entry, ByteString key) {
        boolean afterStart = entry.getStartKey().isEmpty()
                || compareUnsigned(key, entry.getStartKey()) >= 0;
        boolean beforeEnd = entry.getEndKey().isEmpty()
                || compareUnsigned(key, entry.getEndKey()) < 0;
        return afterStart && beforeEnd;
    }

    private static boolean overlaps(RouteEntry entry, ByteString qStart, ByteString qEnd) {
        boolean entryStartsBeforeQueryEnd = qEnd.isEmpty()
                || entry.getStartKey().isEmpty()
                || compareUnsigned(entry.getStartKey(), qEnd) < 0;
        boolean entryEndsAfterQueryStart = qStart.isEmpty()
                || entry.getEndKey().isEmpty()
                || compareUnsigned(entry.getEndKey(), qStart) > 0;
        return entryStartsBeforeQueryEnd && entryEndsAfterQueryStart;
    }

    static int compareUnsigned(ByteString a, ByteString b) {
        int len = Math.min(a.size(), b.size());
        for (int i = 0; i < len; i++) {
            int av = a.byteAt(i) & 0xFF;
            int bv = b.byteAt(i) & 0xFF;
            if (av != bv) {
                return Integer.compare(av, bv);
            }
        }
        return Integer.compare(a.size(), b.size());
    }
}
