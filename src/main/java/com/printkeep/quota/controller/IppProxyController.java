package com.printkeep.quota.controller;

import com.printkeep.quota.ipp.IppPacket;
import com.printkeep.quota.service.PrintQuotaService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import java.io.*;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@RestController
@RequiredArgsConstructor
@Slf4j
public class IppProxyController {

    private final PrintQuotaService printQuotaService;

    @Value("${print-quota.printer-forward-type:IPP}")
    private String printerForwardType;

    @Value("${print-quota.physical-printer-url:}")
    private String physicalPrinterUrl;

    @Value("${print-quota.physical-printer-raw-host:}")
    private String physicalPrinterRawHost;

    @Value("${print-quota.physical-printer-raw-port:9100}")
    private int physicalPrinterRawPort;

    @PostMapping(
            value = "/printers/MainPrinter",
            consumes = "application/ipp",
            produces = "application/ipp"
    )
    public ResponseEntity<byte[]> handleIppRequest(HttpServletRequest request) {
        File tempSpoolFile = null;
        try {
            // 1. Wrap incoming stream to record parsed header bytes
            InputStream rawIn = request.getInputStream();
            IppPacket.RecordingInputStream recordingIn = new IppPacket.RecordingInputStream(rawIn);

            // 2. Parse IPP headers (stops at 0x03 delimiter tag)
            IppPacket ippPacket;
            try {
                ippPacket = IppPacket.parse(recordingIn);
            } catch (Exception e) {
                log.error("Failed to parse incoming IPP headers", e);
                return createBadIppRequestResponse();
            }

            short opId = ippPacket.getOperationOrStatus();
            short version = ippPacket.getVersion();
            int requestId = ippPacket.getRequestId();

            log.info("Received IPP Request: Operation ID = 0x{}, Request ID = {}, Version = 0x{}", 
                    Integer.toHexString(opId), requestId, Integer.toHexString(version));

            // Extract job metadata
            String username = ippPacket.getSingleStringAttribute("requesting-user-name");
            Integer jobImpressions = ippPacket.getSingleIntegerAttribute("job-impressions");
            String sides = ippPacket.getSingleStringAttribute("sides");
            String documentName = ippPacket.getSingleStringAttribute("document-name");
            if (documentName == null) {
                documentName = ippPacket.getSingleStringAttribute("job-name");
            }

            // Defaults if missing
            if (jobImpressions == null) {
                jobImpressions = 1;
            }
            if (sides == null) {
                sides = "one-sided";
            }

            // Support both Print-Job (0x0002) and Validate-Job (0x0004)
            boolean isPrintJob = (opId == 0x0002);
            boolean isValidateJob = (opId == 0x0004);

            if (!isPrintJob && !isValidateJob) {
                log.warn("Unsupported IPP operation: 0x{}. Rejecting.", Integer.toHexString(opId));
                byte[] errorBytes = IppPacket.createErrorResponse(version, requestId, "Unsupported IPP operation");
                return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/ipp")).body(errorBytes);
            }

            // 3. Process quota rules
            PrintQuotaService.QuotaCheckResult checkResult = printQuotaService.processPrintJobQuota(
                    username, jobImpressions, sides, documentName
            );

            if (!checkResult.isAllowed()) {
                log.warn("Job rejected for user {}: {}", username, checkResult.getMessage());
                byte[] errorBytes = IppPacket.createErrorResponse(version, requestId, checkResult.getMessage());
                return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/ipp")).body(errorBytes);
            }

            // If it is just a Validate-Job operation, we don't spool data, we just return success or forward it
            if (isValidateJob) {
                log.info("Validate-Job request allowed for user {}", username);
                if ("IPP".equalsIgnoreCase(printerForwardType)) {
                    // Forward validation to physical printer
                    byte[] forwardResponse = forwardIppRequestToPrinter(recordingIn.getRecordedBytes());
                    return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/ipp")).body(forwardResponse);
                } else {
                    // Raw printing doesn't validate, so we return a standard successful IPP response
                    byte[] successBytes = createSuccessIppResponse(version, requestId);
                    return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/ipp")).body(successBytes);
                }
            }

            // 4. Spool the remaining data to a temporary file
            tempSpoolFile = File.createTempFile("print-spool-", ".tmp");
            try (FileOutputStream fos = new FileOutputStream(tempSpoolFile)) {
                // Write the headers we already read
                fos.write(recordingIn.getRecordedBytes());
                
                // Read and write the remaining body (print data)
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = recordingIn.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }

            log.info("Print job spooled to temp file: {} (Size: {} bytes)", 
                    tempSpoolFile.getAbsolutePath(), tempSpoolFile.length());

            // 5. Forward print data to physical printer
            byte[] printerResponseBytes;
            if ("IPP".equalsIgnoreCase(printerForwardType)) {
                printerResponseBytes = forwardIppFileToPrinter(tempSpoolFile);
            } else if ("RAW".equalsIgnoreCase(printerForwardType)) {
                forwardRawFileToPrinter(tempSpoolFile);
                // For RAW forwarding, return standard IPP successful response
                printerResponseBytes = createSuccessIppResponse(version, requestId);
            } else {
                throw new IllegalStateException("Unknown printer-forward-type: " + printerForwardType);
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/ipp"))
                    .body(printerResponseBytes);

        } catch (Exception e) {
            log.error("Error handling print job", e);
            // Return server-side failure response as IPP error rather than crashing
            byte[] errorBytes = IppPacket.createErrorResponse((short) 0x0200, 1, "Internal server error: " + e.getMessage());
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/ipp")).body(errorBytes);
        } finally {
            if (tempSpoolFile != null && tempSpoolFile.exists()) {
                boolean deleted = tempSpoolFile.delete();
                if (deleted) {
                    log.debug("Temporary spool file deleted successfully.");
                } else {
                    log.warn("Failed to delete temporary spool file: {}", tempSpoolFile.getAbsolutePath());
                }
            }
        }
    }

    private byte[] forwardIppRequestToPrinter(byte[] payload) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(physicalPrinterUrl))
                .header("Content-Type", "application/ipp")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                .build();

        log.info("Forwarding IPP metadata request to physical printer at {}", physicalPrinterUrl);
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        
        if (response.statusCode() != 200) {
            throw new IOException("Physical printer returned non-OK status: " + response.statusCode());
        }
        return response.body();
    }

    private byte[] forwardIppFileToPrinter(File file) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(physicalPrinterUrl))
                .header("Content-Type", "application/ipp")
                .timeout(Duration.ofMinutes(5)) // Long timeout for large print jobs
                .POST(HttpRequest.BodyPublishers.ofFile(file.toPath()))
                .build();

        log.info("Forwarding full spooled print job to physical printer at {}", physicalPrinterUrl);
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            throw new IOException("Physical printer returned non-OK status: " + response.statusCode());
        }
        return response.body();
    }

    private void forwardRawFileToPrinter(File file) throws IOException {
        log.info("Forwarding raw print job via TCP Socket to {}:{}", physicalPrinterRawHost, physicalPrinterRawPort);
        try (Socket socket = new Socket(physicalPrinterRawHost, physicalPrinterRawPort);
             OutputStream out = socket.getOutputStream();
             FileInputStream fis = new FileInputStream(file)) {
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            out.flush();
            log.info("Successfully transmitted raw bytes to physical printer.");
        }
    }

    private byte[] createSuccessIppResponse(short version, int requestId) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        try {
            dos.writeShort(version);
            dos.writeShort(0x0000); // successful-ok status code
            dos.writeInt(requestId);

            // Operation attributes group tag
            dos.writeByte(0x01);

            // attributes-charset
            dos.writeByte(0x47);
            writeAttributeHeader(dos, "attributes-charset");
            writeAttributeValue(dos, "utf-8");

            // attributes-natural-language
            dos.writeByte(0x48);
            writeAttributeHeader(dos, "attributes-natural-language");
            writeAttributeValue(dos, "en-us");

            dos.writeByte(0x03); // End tag
        } catch (IOException e) {
            // Ignored
        }
        return baos.toByteArray();
    }

    private void writeAttributeHeader(DataOutputStream dos, String name) throws IOException {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        dos.writeShort(nameBytes.length);
        dos.write(nameBytes);
    }

    private void writeAttributeValue(DataOutputStream dos, String value) throws IOException {
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
        dos.writeShort(valueBytes.length);
        dos.write(valueBytes);
    }

    private ResponseEntity<byte[]> createBadIppRequestResponse() {
        byte[] errorBytes = IppPacket.createErrorResponse((short) 0x0200, 1, "Bad Request: Failed to parse IPP content");
        return ResponseEntity.status(HttpStatus.OK)
                .contentType(MediaType.parseMediaType("application/ipp"))
                .body(errorBytes);
    }
}
