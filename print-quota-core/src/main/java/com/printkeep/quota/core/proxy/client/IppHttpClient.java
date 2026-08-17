package com.printkeep.quota.core.proxy.client;

import com.printkeep.quota.core.proxy.routing.PrinterConfig;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Standard HTTP/HTTPS client using Java 11 HttpClient, supporting connection pooling,
 * chunked streaming, and configurable retry policies for printers.
 */
@Component
public class IppHttpClient {

    private static final Logger log = LoggerFactory.getLogger(IppHttpClient.class);

    private final HttpClient httpClient;
    private final PrinterConfig config;
    private final ExecutorService executor;

    public IppHttpClient(final PrinterConfig config) {
        this.config = config;

        this.executor = new ThreadPoolExecutor(
                config.getForwardingCoreThreads(),
                config.getForwardingMaxThreads(),
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(config.getForwardingQueueCapacity()),
                new NamedThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy());

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
                .executor(this.executor)
                .build();
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (final InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Sends a binary IPP input stream to the target printer URI and returns the response stream.
     *
     * @param printerUri   destination printer URI.
     * @param requestStream binary input stream of the request.
     * @return the printer response input stream.
     * @throws IOException          if transport errors occur.
     * @throws InterruptedException if thread is interrupted.
     */
    public InputStream sendStream(final String printerUri, final InputStream requestStream)
            throws IOException, InterruptedException {

        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(printerUri))
                .header("Content-Type", "application/ipp")
                .header("Accept", "application/ipp")
                .timeout(Duration.ofMillis(config.getReadTimeoutMs()))
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> requestStream))
                .build();

        int attempts = 0;
        final int maxRetries = config.getMaxRetryCount();
        final int delay = config.getRetryDelayMs();

        while (true) {
            attempts++;
            try {
                log.debug("Sending IPP payload to {} (Attempt {}/{})", printerUri, attempts, maxRetries);
                final HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() >= 500) {
                    throw new IOException("Target printer returned server error code: " + response.statusCode());
                }
                return response.body();
            } catch (final ConnectException | SocketTimeoutException | java.net.http.HttpTimeoutException e) {
                log.warn("Temporary network disconnect to printer {} on attempt {}/{}", printerUri, attempts, maxRetries, e);
                if (attempts >= maxRetries) {
                    throw e;
                }
                Thread.sleep(delay);
            } catch (final IOException e) {
                // Determine if this is a connection reset or network error we want to retry
                final String msg = e.getMessage() != null ? e.getMessage().toLowerCase(Locale.ROOT) : "";
                if (msg.contains("connection reset") || msg.contains("broken pipe")
                        || msg.contains("connection closed") || msg.contains("closed before")
                        || msg.contains("closed request") || msg.contains("closed session")
                        || msg.contains("billing/ipp request closed")
                        || msg.contains("header parser") || msg.contains("received no bytes")) {
                    log.warn("Connection closed or reset by printer {} on attempt {}/{}", printerUri, attempts, maxRetries, e);
                    if (attempts >= maxRetries) {
                        throw e;
                    }
                    Thread.sleep(delay);
                } else {
                    log.error("RETRIES FALLTHROUGH: class={}, msg={}", e.getClass().getName(), e.getMessage());
                    throw e;
                }
            }
        }
    }

    private static final class NamedThreadFactory implements java.util.concurrent.ThreadFactory {

        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(final Runnable runnable) {
            final Thread thread = new Thread(runnable, "ipp-forwarder-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
