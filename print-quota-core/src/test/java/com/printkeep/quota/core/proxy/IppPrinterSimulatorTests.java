package com.printkeep.quota.core.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.printkeep.quota.core.proxy.client.IppHttpClient;
import com.printkeep.quota.core.proxy.routing.PrinterConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

class IppPrinterSimulatorTests {

    private HttpServer server;
    private int port;
    private final AtomicInteger requestCounter = new AtomicInteger(0);
    private PrinterConfig printerConfig;
    private IppHttpClient ippHttpClient;
    private java.util.concurrent.ExecutorService serverExecutor;

    @BeforeEach
    void startServerAndSetUp() throws IOException {
        requestCounter.set(0);

        // Start JDK built-in HttpServer on an ephemeral local port per test for isolation
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/ipp/print", new SimulatorHttpHandler());
        serverExecutor = java.util.concurrent.Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.start();

        printerConfig = new PrinterConfig();
        // Default configuration for fast tests
        printerConfig.setConnectTimeoutMs(500);
        printerConfig.setReadTimeoutMs(3000); // 3 seconds default to prevent JIT flakes
        printerConfig.setMaxRetryCount(3);
        printerConfig.setRetryDelayMs(50);
        printerConfig.setForwardingCoreThreads(2);
        printerConfig.setForwardingMaxThreads(4);
        printerConfig.setForwardingQueueCapacity(10);

        ippHttpClient = new IppHttpClient(printerConfig);
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
        if (ippHttpClient != null) {
            ippHttpClient.shutdown();
        }
    }

    @Test
    void testForwardingSuccess() throws Exception {
        final String uri = "http://127.0.0.1:" + port + "/ipp/print?behavior=SUCCESS";
        final byte[] requestBody = "MOCK_IPP_REQUEST".getBytes(StandardCharsets.UTF_8);

        try (final InputStream in = ippHttpClient.sendStream(uri, new ByteArrayInputStream(requestBody))) {
            final byte[] responseBytes = in.readAllBytes();
            assertThat(new String(responseBytes, StandardCharsets.UTF_8)).isEqualTo("PRINTER_OK");
        }
        assertThat(requestCounter.get()).isEqualTo(1);
    }

    @Test
    void testForwardingHttpErrorDoesNotRetry() {
        final String uri = "http://127.0.0.1:" + port + "/ipp/print?behavior=HTTP_ERROR";
        final byte[] requestBody = "MOCK_IPP_REQUEST".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> ippHttpClient.sendStream(uri, new ByteArrayInputStream(requestBody)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("500");

        // HTTP 500 errors should not trigger client retries
        assertThat(requestCounter.get()).isEqualTo(1);
    }

    @Test
    void testForwardingTimeoutRetriesAndFails() {
        printerConfig.setReadTimeoutMs(200); // Set very short timeout for verification
        final String uri = "http://127.0.0.1:" + port + "/ipp/print?behavior=TIMEOUT";
        final byte[] requestBody = "MOCK_IPP_REQUEST".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> ippHttpClient.sendStream(uri, new ByteArrayInputStream(requestBody)))
                .isInstanceOf(IOException.class);

        // Verification of retry logic: 1 original attempt + 2 retries = 3 hits total
        assertThat(requestCounter.get()).isEqualTo(3);
    }

    @Test
    void testForwardingConnectionResetRetriesAndFails() {
        final String uri = "http://127.0.0.1:" + port + "/ipp/print?behavior=CONNECTION_RESET";
        final byte[] requestBody = "MOCK_IPP_REQUEST".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> ippHttpClient.sendStream(uri, new ByteArrayInputStream(requestBody)))
                .isInstanceOf(IOException.class);

        // Verifies the retry logic on connection reset
        assertThat(requestCounter.get()).isEqualTo(3);
    }

    @Test
    void testForwardingSlowResponseSucceedsWithinTimeout() throws Exception {
        printerConfig.setReadTimeoutMs(1500);
        final String uri = "http://127.0.0.1:" + port + "/ipp/print?behavior=SLOW_RESPONSE";
        final byte[] requestBody = "MOCK_IPP_REQUEST".getBytes(StandardCharsets.UTF_8);

        try (final InputStream in = ippHttpClient.sendStream(uri, new ByteArrayInputStream(requestBody))) {
            final byte[] responseBytes = in.readAllBytes();
            assertThat(new String(responseBytes, StandardCharsets.UTF_8)).isEqualTo("PRINTER_OK");
        }
        assertThat(requestCounter.get()).isEqualTo(1);
    }

    @Test
    void testForwardingEmptyAndMalformedResponse() throws Exception {
        final String uriEmpty = "http://127.0.0.1:" + port + "/ipp/print?behavior=EMPTY_RESPONSE";
        try (final InputStream in = ippHttpClient.sendStream(uriEmpty, new ByteArrayInputStream(new byte[0]))) {
            assertThat(in.read()).isEqualTo(-1);
        }

        final String uriMalformed = "http://127.0.0.1:" + port + "/ipp/print?behavior=MALFORMED_RESPONSE";
        try (final InputStream in = ippHttpClient.sendStream(uriMalformed, new ByteArrayInputStream(new byte[0]))) {
            final byte[] responseBytes = in.readAllBytes();
            assertThat(responseBytes).containsExactly(0x00, 0x01, 0x02, 0x03);
        }
    }

    @Test
    void testForwardingLargeResponse() throws Exception {
        final String uri = "http://127.0.0.1:" + port + "/ipp/print?behavior=LARGE_RESPONSE";
        try (final InputStream in = ippHttpClient.sendStream(uri, new ByteArrayInputStream(new byte[0]))) {
            int totalBytesRead = 0;
            final byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                totalBytesRead += read;
            }
            assertThat(totalBytesRead).isEqualTo(100 * 1024); // 100 KB
        }
    }

    private class SimulatorHttpHandler implements HttpHandler {
        @Override
        public void handle(final HttpExchange exchange) throws IOException {
            requestCounter.incrementAndGet();
            final String query = exchange.getRequestURI().getQuery();
            String behavior = "SUCCESS";
            if (query != null && query.contains("behavior=")) {
                behavior = query.split("behavior=")[1].split("&")[0];
            }

            // Consume request body
            try (final InputStream in = exchange.getRequestBody()) {
                in.readAllBytes();
            }

            switch (behavior) {
                case "SLOW_RESPONSE":
                    try {
                        Thread.sleep(400); // Wait 400ms (timeout is 1500ms, should succeed)
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    sendOk(exchange, "PRINTER_OK");
                    break;

                case "TIMEOUT":
                    try {
                        Thread.sleep(2000); // Sleep longer than client's readTimeoutMs (200ms)
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    sendOk(exchange, "TIMEOUT_DONE");
                    break;

                case "CONNECTION_RESET":
                    // Abort connection: close the TCP socket instantly by closing the exchange/underlying connection
                    exchange.close();
                    break;

                case "HTTP_ERROR":
                    final byte[] errResponse = "Internal Server Error".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(500, errResponse.length);
                    try (final OutputStream os = exchange.getResponseBody()) {
                        os.write(errResponse);
                    }
                    break;

                case "EMPTY_RESPONSE":
                    exchange.sendResponseHeaders(200, -1); // Chunked empty body
                    exchange.close();
                    break;

                case "MALFORMED_RESPONSE":
                    final byte[] garbage = {0x00, 0x01, 0x02, 0x03};
                    exchange.sendResponseHeaders(200, garbage.length);
                    try (final OutputStream os = exchange.getResponseBody()) {
                        os.write(garbage);
                    }
                    break;

                case "LARGE_RESPONSE":
                    final int size = 100 * 1024; // 100 KB
                    exchange.sendResponseHeaders(200, size);
                    try (final OutputStream os = exchange.getResponseBody()) {
                        final byte[] buffer = new byte[4096];
                        for (int i = 0; i < size; i += buffer.length) {
                            os.write(buffer, 0, Math.min(buffer.length, size - i));
                        }
                    }
                    break;

                case "SUCCESS":
                default:
                    sendOk(exchange, "PRINTER_OK");
                    break;
            }
        }

        private void sendOk(final HttpExchange exchange, final String content) throws IOException {
            final byte[] response = content.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (final OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }
    }
}
