package org.example.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.example.service.FrontierServiceImpl;
import org.example.thrift.FrontierService;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class ThriftServerConfig {

    private final FrontierServiceImpl frontierService;

    @Value("${thrift.server.port:9090}")
    private int port;

    private TThreadPoolServer server;

    public ThriftServerConfig(FrontierServiceImpl frontierService) {
        this.frontierService = frontierService;
    }

    @PostConstruct
    public void start() throws Exception {
        TServerTransport serverTransport = new TServerSocket(port);
        FrontierService.Processor<FrontierServiceImpl> processor = new FrontierService.Processor<>(frontierService);
        TCompactProtocol.Factory protocolFactory = new TCompactProtocol.Factory();

        server = new TThreadPoolServer(new TThreadPoolServer.Args(serverTransport)
                .processor(processor)
                .protocolFactory(protocolFactory)
                .minWorkerThreads(2)
                .maxWorkerThreads(10));

        log.info("Starting Thrift server on port: {}", port);
        new Thread(server::serve).start();
        log.info("Thrift server started successfully on port: {}", port);
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            log.info("Stopping Thrift server...");
            server.stop();
            log.info("Thrift server stopped.");
        }
    }
}