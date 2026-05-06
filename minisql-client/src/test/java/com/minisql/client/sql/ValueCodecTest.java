package com.minisql.client.sql;

import com.google.protobuf.ByteString;
import com.minisql.common.proto.ColumnSchema;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ValueCodecTest {

    @Test
    public void bigintRoundTrip() {
        ColumnSchema col = bigint("id");
        for (long v : new long[]{Long.MIN_VALUE, -1, 0, 1, 42, Long.MAX_VALUE}) {
            ByteString encoded = ValueCodec.encode(col, v);
            assertEquals(v, ValueCodec.decode(col, encoded));
        }
    }

    @Test
    public void bigintEncodingIsOrderPreserving() {
        ColumnSchema col = bigint("id");
        long[] sorted = {Long.MIN_VALUE, -1_000_000L, -1, 0, 1, 42, 1_000_000L, Long.MAX_VALUE};
        for (int i = 1; i < sorted.length; i++) {
            ByteString lo = ValueCodec.encode(col, sorted[i - 1]);
            ByteString hi = ValueCodec.encode(col, sorted[i]);
            assertTrue("order broken between " + sorted[i - 1] + " and " + sorted[i],
                    compareUnsigned(lo, hi) < 0);
        }
    }

    @Test
    public void varcharRoundTrip() {
        ColumnSchema col = ColumnSchema.newBuilder()
                .setName("name").setType("VARCHAR(50)").setNullable(true).build();
        ByteString encoded = ValueCodec.encode(col, "hello 世界");
        assertEquals("hello 世界", ValueCodec.decode(col, encoded));
    }

    @Test
    public void doubleRoundTrip() {
        ColumnSchema col = ColumnSchema.newBuilder()
                .setName("score").setType("DOUBLE").setNullable(true).build();
        ByteString encoded = ValueCodec.encode(col, 3.14159);
        assertEquals(3.14159, (double) ValueCodec.decode(col, encoded), 1e-9);
    }

    @Test
    public void typeWithLengthSpecifierIsAccepted() {
        ColumnSchema col = ColumnSchema.newBuilder()
                .setName("s").setType("varchar(255)").setNullable(true).build();
        assertEquals("abc", ValueCodec.decode(col, ValueCodec.encode(col, "abc")));
    }

    private static ColumnSchema bigint(String name) {
        return ColumnSchema.newBuilder()
                .setName(name).setType("BIGINT").setNullable(false).build();
    }

    private static int compareUnsigned(ByteString a, ByteString b) {
        int n = Math.min(a.size(), b.size());
        for (int i = 0; i < n; i++) {
            int av = a.byteAt(i) & 0xFF;
            int bv = b.byteAt(i) & 0xFF;
            if (av != bv) return Integer.compare(av, bv);
        }
        return Integer.compare(a.size(), b.size());
    }
}
