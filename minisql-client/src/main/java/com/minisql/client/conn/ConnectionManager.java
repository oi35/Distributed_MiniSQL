package com.minisql.client.conn;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ConnectionManager implements AutoCloseable {

    private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();
    private final ChannelFactory channelFactory;
    private volatile boolean closed = false;

    public ConnectionManager() {
        this(address -> ManagedChannelBuilder.forTarget(address)
                .usePlaintext()
                .keepAliveTime(30, TimeUnit.SECONDS)
                .build());
    }

    public ConnectionManager(ChannelFactory channelFactory) {
        if (channelFactory == null) {
            throw new IllegalArgumentException("channelFactory must not be null");
        }
        this.channelFactory = channelFactory;
    }

    public ManagedChannel getChannel(String address) {
        if (closed) {
            throw new IllegalStateException("ConnectionManager is closed");
        }
        if (address == null || address.isEmpty()) {
            throw new IllegalArgumentException("address must not be empty");
        }
        return channels.computeIfAbsent(address, channelFactory::create);
    }

    public int size() {
        return channels.size();
    }

    @Override
    public void close() {
        closed = true;
        for (ManagedChannel channel : channels.values()) {
            channel.shutdown();
        }
        for (ManagedChannel channel : channels.values()) {
            try {
                if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                    channel.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                channel.shutdownNow();
            }
        }
        channels.clear();
    }

    @FunctionalInterface
    public interface ChannelFactory {
        ManagedChannel create(String address);
    }
}
