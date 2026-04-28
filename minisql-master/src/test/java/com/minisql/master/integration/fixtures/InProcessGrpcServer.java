package com.minisql.master.integration.fixtures;

import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;

public class InProcessGrpcServer {
    private Server server;
    private String serverName;
    private InProcessServerBuilder builder;

    public void start(String serverName) throws Exception {
        this.serverName = serverName;
        if (builder == null) {
            builder = InProcessServerBuilder.forName(serverName).directExecutor();
        }
        server = builder.build().start();
    }

    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    public void addService(BindableService service) {
        if (builder == null) {
            builder = InProcessServerBuilder.forName(serverName).directExecutor();
        }
        builder.addService(service);
    }

    public ManagedChannel createChannel() {
        return InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();
    }
}
