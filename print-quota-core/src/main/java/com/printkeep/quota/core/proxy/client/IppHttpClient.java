package com.printkeep.quota.core.proxy.client;

import com.printkeep.quota.core.proxy.routing.PrinterConfig;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    public IppHttpClient(final PrinterConfig config) {
        this.config = config;

        // Custom executor service to pool and manage worker threads for connection reuse
        final ExecutorService executor = Executors.newCachedThreadPool();

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
                .executor(executor)
                .build();
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
            } catch (final ConnectException | SocketTimeoutException e) {
                log.warn("Temporary network disconnect to printer {} on attempt {}/{}", printerUri, attempts, maxRetries, e);
                if (attempts >= maxRetries) {
                    throw e;
                }
                Thread.sleep(delay);
            } catch (final IOException e) {
                // Determine if this is a connection reset or network error we want to retry
                final String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                if (msg.contains("connection reset") || msg.contains("broken pipe")) {
                    log.warn("Connection reset by printer {} on attempt {}/{}", printerUri, attempts, maxRetries, e);
                    if (attempts >= maxRetries) {
                        throw e;
                    }
                    Thread.sleep(delay);
                } else {
                    throw e;
                }
            }
        }
    }
}
