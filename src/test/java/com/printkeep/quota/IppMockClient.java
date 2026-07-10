package com.printkeep.quota;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * A utility class to generate and send a mock IPP Print-Job request to the middleware for manual testing.
 * Useful for local verification of SSL, request routing, quota verification, database updating, and spooling.
 */
public class IppMockClient {

    public static void main(String[] args) {
        String url = "https://localhost:443/printers/MainPrinter";
        if (args.length > 0) {
            url = args[0];
        }

        String username = "test.user";
        if (args.length > 1) {
            username = args[1];
        }

        int impressions = 3;
        String sides = "two-sided-long-edge"; // Should count as 6 pages
        String docName = "test-document.pdf";

        System.out.println("Generating mock IPP Print-Job payload for: " + username);
        System.out.println("Impressions: " + impressions + ", Sides: " + sides + ", Document: " + docName);

        try {
            byte[] payload = generateIppPrintJobPayload(username, impressions, sides, docName);
            System.out.println("Payload generated. Size: " + payload.length + " bytes.");
            System.out.println("Sending POST request to: " + url);

            // Configure HTTP Client to trust self-signed Jetty certificate for test purposes
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return null; }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            }}, new SecureRandom());

            HttpClient client = HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/ipp")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                    .build();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            System.out.println("Response Status Code: " + response.statusCode());
            System.out.println("Response Body Length: " + response.body().length + " bytes");

            // Simple parser to print response status
            if (response.body().length >= 8) {
                byte[] body = response.body();
                short version = (short) (((body[0] & 0xFF) << 8) | (body[1] & 0xFF));
                short status = (short) (((body[2] & 0xFF) << 8) | (body[3] & 0xFF));
                int reqId = (((body[4] & 0xFF) << 24) | ((body[5] & 0xFF) << 16) | ((body[6] & 0xFF) << 8) | (body[7] & 0xFF));
                
                System.out.println("\n--- IPP Response Info ---");
                System.out.printf("Version: 0x%04X\n", version);
                System.out.printf("Status Code: 0x%04X (%s)\n", status, getStatusCodeDescription(status));
                System.out.printf("Request ID: %d\n", reqId);
                
                // Print any message if found
                String responseStr = new String(body, StandardCharsets.UTF_8);
                if (responseStr.contains("status-message")) {
                    int idx = responseStr.indexOf("status-message");
                    System.out.println("Contains status-message in payload.");
                }
            }

        } catch (Exception e) {
            System.err.println("Error sending mock IPP request:");
            e.printStackTrace();
        }
    }

    private static byte[] generateIppPrintJobPayload(String username, int impressions, String sides, String docName) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        // Version (2.0)
        dos.writeByte(0x02);
        dos.writeByte(0x00);
        // Operation: Print-Job (0x0002)
        dos.writeShort(0x0002);
        // Request ID
        dos.writeInt(9999);

        // 1. Operation attributes group
        dos.writeByte(0x01);

        // attributes-charset
        dos.writeByte(0x47);
        writeAttribute(dos, "attributes-charset", "utf-8");

        // attributes-natural-language
        dos.writeByte(0x48);
        writeAttribute(dos, "attributes-natural-language", "en-us");

        // requesting-user-name
        dos.writeByte(0x42);
        writeAttribute(dos, "requesting-user-name", username);

        // 2. Job attributes group
        dos.writeByte(0x02);

        // job-impressions
        dos.writeByte(0x21); // integer value tag
        writeAttributeHeader(dos, "job-impressions");
        dos.writeShort(4); // 4 bytes integer
        dos.writeInt(impressions);

        // sides
        dos.writeByte(0x44); // keyword value tag
        writeAttribute(dos, "sides", sides);

        // job-name / document-name
        dos.writeByte(0x42);
        writeAttribute(dos, "document-name", docName);

        // End attributes
        dos.writeByte(0x03);

        // Mock spool data
        dos.write("PDF-MOCK-CONTENT-SPOOL-DATA-LINE-1\n".getBytes(StandardCharsets.UTF_8));
        dos.write("PDF-MOCK-CONTENT-SPOOL-DATA-LINE-2\n".getBytes(StandardCharsets.UTF_8));

        return baos.toByteArray();
    }

    private static void writeAttribute(DataOutputStream dos, String name, String value) throws IOException {
        writeAttributeHeader(dos, name);
        byte[] valBytes = value.getBytes(StandardCharsets.UTF_8);
        dos.writeShort(valBytes.length);
        dos.write(valBytes);
    }

    private static void writeAttributeHeader(DataOutputStream dos, String name) throws IOException {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        dos.writeShort(nameBytes.length);
        dos.write(nameBytes);
    }

    private static String getStatusCodeDescription(short code) {
        switch (code) {
            case 0x0000: return "successful-ok";
            case 0x040B: return "client-error-not-possible (Out of Quota / Invalid Request)";
            case 0x0400: return "client-error-bad-request";
            case 0x0500: return "server-error-internal-error";
            default: return "unknown status code";
        }
    }
}
