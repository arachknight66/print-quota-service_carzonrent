package com.printkeep.quota.core.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.printkeep.quota.codec.model.IppAttribute;
import com.printkeep.quota.codec.model.IppAttributeGroup;
import com.printkeep.quota.codec.model.IppPacket;
import com.printkeep.quota.codec.model.IppTag;
import com.printkeep.quota.codec.parser.IppEncoder;
import com.printkeep.quota.codec.parser.IppParser;
import com.printkeep.quota.core.processing.decision.IppDecision;
import com.printkeep.quota.core.processing.pipeline.PipelineResult;
import com.printkeep.quota.core.processing.pipeline.PrintProcessingPipeline;
import com.printkeep.quota.core.proxy.client.IppHttpClient;
import com.printkeep.quota.core.proxy.routing.PrinterConfig;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import com.printkeep.quota.core.AbstractIntegrationTest;

/**
 * End-to-end integration tests verifying IPP Proxy endpoint interception,
 * validation pipelines, and forwarding behaviors.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IppProxyIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PrinterConfig printerConfig;

    @MockBean
    private PrintProcessingPipeline pipeline;

    @MockBean
    private IppHttpClient ippHttpClient;

    @BeforeEach
    void setUp() {
        printerConfig.setPrinters(Map.of("LaserJet_5", "http://printer1.company.local:631/ipp/print"));
    }

    @Test
    void testProxyControllerAllowsAndForwardsJob() throws Exception {
        // Build mock IPP request bytes
        final IppAttribute reqUser = new IppAttribute("requesting-user-name", IppTag.NAME_WITHOUT_LANGUAGE, List.of("jdoe"));
        final IppAttribute printerUri = new IppAttribute("printer-uri", IppTag.URI, List.of("ipp://localhost/printers/LaserJet_5"));
        final IppAttributeGroup opGroup = new IppAttributeGroup(IppTag.OPERATION_ATTRIBUTES, List.of(reqUser, printerUri));
        final IppPacket requestPacket = new IppPacket((byte) 2, (byte) 0, (short) 0x0002, 444, List.of(opGroup), new byte[0]);

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        IppEncoder.encode(requestPacket, out);
        final byte[] rawIppBytes = out.toByteArray();

        // Pipeline ALLOW decision mock
        final PipelineResult pipelineResult = new PipelineResult(
                IppDecision.ALLOW, "Success", "corr-999", 5, 1, "jdoe", "LaserJet_5", 5
        );
        when(pipeline.process(any(IppPacket.class), anyString(), anyString())).thenReturn(pipelineResult);

        // HTTP Forwarding client mock returning simulated printer response
        final InputStream printerResponseStream = new ByteArrayInputStream("PRINTER_OK".getBytes(StandardCharsets.UTF_8));
        when(ippHttpClient.sendStream(eq("http://printer1.company.local:631/ipp/print"), any(InputStream.class)))
                .thenReturn(printerResponseStream);

        final HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/ipp");
        final HttpEntity<byte[]> requestEntity = new HttpEntity<>(rawIppBytes, headers);

        final ResponseEntity<byte[]> response = restTemplate.postForEntity(
                "/printers/LaserJet_5", requestEntity, byte[].class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(response.getBody(), StandardCharsets.UTF_8)).isEqualTo("PRINTER_OK");

        verify(ippHttpClient, times(1)).sendStream(eq("http://printer1.company.local:631/ipp/print"), any(InputStream.class));
    }

    @Test
    void testProxyControllerRejectsAndReturnsIppError() throws Exception {
        // Build mock IPP request bytes
        final IppAttribute reqUser = new IppAttribute("requesting-user-name", IppTag.NAME_WITHOUT_LANGUAGE, List.of("jdoe"));
        final IppAttribute printerUri = new IppAttribute("printer-uri", IppTag.URI, List.of("ipp://localhost/printers/LaserJet_5"));
        final IppAttributeGroup opGroup = new IppAttributeGroup(IppTag.OPERATION_ATTRIBUTES, List.of(reqUser, printerUri));
        final IppPacket requestPacket = new IppPacket((byte) 2, (byte) 0, (short) 0x0002, 445, List.of(opGroup), new byte[0]);

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        IppEncoder.encode(requestPacket, out);
        final byte[] rawIppBytes = out.toByteArray();

        // Pipeline REJECT decision mock
        final PipelineResult pipelineResult = new PipelineResult(
                IppDecision.REJECT_INSUFFICIENT_QUOTA, "Insufficient balance", "corr-888", 5, 1, "jdoe", "LaserJet_5", 5
        );
        when(pipeline.process(any(IppPacket.class), anyString(), anyString())).thenReturn(pipelineResult);

        final HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/ipp");
        final HttpEntity<byte[]> requestEntity = new HttpEntity<>(rawIppBytes, headers);

        final ResponseEntity<byte[]> response = restTemplate.postForEntity(
                "/printers/LaserJet_5", requestEntity, byte[].class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Decode returned response bytes - it must be a valid IPP packet showing reject code 0x0401 (Not Authorized)
        final ByteArrayInputStream in = new ByteArrayInputStream(response.getBody());
        final IppPacket errorPacket = IppParser.parse(in);

        assertThat(errorPacket.operationOrStatus()).isEqualTo((short) 0x0401); // client-error-not-authorized
        assertThat(errorPacket.transactionId()).isEqualTo(445);
        verify(ippHttpClient, never()).sendStream(anyString(), any(InputStream.class));
    }
}
