package com.minisql.client.conn;

import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ConnectionManagerTest {

    @Test
    public void getChannelCachesPerAddress() {
        AtomicInteger created = new AtomicInteger();
        ConnectionManager manager = new ConnectionManager(addr -> {
            created.incrementAndGet();
            return new StubChannel();
        });

        ManagedChannel a1 = manager.getChannel("host-a:8001");
        ManagedChannel a2 = manager.getChannel("host-a:8001");
        ManagedChannel b1 = manager.getChannel("host-b:8002");

        assertSame(a1, a2);
        assertNotNull(b1);
        assertEquals(2, created.get());
        assertEquals(2, manager.size());
    }

    @Test
    public void closeShutsDownAllChannels() {
        StubChannel channel = new StubChannel();
        ConnectionManager manager = new ConnectionManager(addr -> channel);
        manager.getChannel("host:1");

        manager.close();

        assertTrue(channel.shutdownCalled.get());
        assertEquals(0, manager.size());
    }

    @Test
    public void getChannelAfterCloseThrows() {
        ConnectionManager manager = new ConnectionManager(addr -> new StubChannel());
        manager.close();

        assertThrows(IllegalStateException.class, () -> manager.getChannel("host:1"));
    }

    @Test
    public void getChannelRejectsEmptyAddress() {
        ConnectionManager manager = new ConnectionManager(addr -> new StubChannel());
        assertThrows(IllegalArgumentException.class, () -> manager.getChannel(""));
        assertThrows(IllegalArgumentException.class, () -> manager.getChannel(null));
    }

    private static final class StubChannel extends ManagedChannel {
        final AtomicBoolean shutdownCalled = new AtomicBoolean();
        final AtomicBoolean terminated = new AtomicBoolean();

        @Override
        public ManagedChannel shutdown() {
            shutdownCalled.set(true);
            terminated.set(true);
            return this;
        }

        @Override
        public boolean isShutdown() { return shutdownCalled.get(); }

        @Override
        public boolean isTerminated() { return terminated.get(); }

        @Override
        public ManagedChannel shutdownNow() {
            shutdownCalled.set(true);
            terminated.set(true);
            return this;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(
                MethodDescriptor<ReqT, RespT> methodDescriptor, CallOptions callOptions) {
            throw new UnsupportedOperationException("stub channel");
        }

        @Override
        public String authority() { return "stub"; }
    }
}
