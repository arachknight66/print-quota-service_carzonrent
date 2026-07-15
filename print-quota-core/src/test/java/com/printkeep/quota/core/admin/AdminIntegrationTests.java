package com.printkeep.quota.core.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.printkeep.quota.core.AbstractIntegrationTest;
import com.printkeep.quota.core.admin.dto.DashboardSummary;
import com.printkeep.quota.core.admin.service.ExcelExportService;
import com.printkeep.quota.core.model.PrintLog;
import com.printkeep.quota.core.model.PrintStatus;
import com.printkeep.quota.core.model.Quota;
import com.printkeep.quota.core.model.User;
import com.printkeep.quota.core.repository.PrintLogRepository;
import com.printkeep.quota.core.repository.QuotaRepository;
import com.printkeep.quota.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * End-to-end integration tests validating reporting queries, dashboard statistics,
 * and admin REST APIs.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminIntegrationTests extends AbstractIntegrationTest {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private QuotaRepository quotaRepository;

    @Autowired
    private PrintLogRepository printLogRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ExcelExportService exportService;

    @Autowired
    private com.printkeep.quota.core.processing.pipeline.PrintProcessingPipeline pipeline;

    @Autowired
    private com.printkeep.quota.core.repository.QuotaAdjustmentLogRepository adjustmentLogRepository;

    private TransactionTemplate transactionTemplate;
    private User savedUser;

    @BeforeEach
    void setUp() {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.savedUser = transactionTemplate.execute(status -> {
            printLogRepository.deleteAll();
            quotaRepository.deleteAll();
            userRepository.deleteAll();

            final User user = new User();
            user.setDomainUsername("company\\adminuser");
            user.setDepartment("Finance");
            final User saved = userRepository.saveAndFlush(user);

            final Quota quota = new Quota();
            quota.setUser(saved);
            quota.setMonth(MONTH_FORMATTER.format(Instant.now()));
            quota.setAllocatedPages(200);
            quota.setUsedPages(50);
            quotaRepository.saveAndFlush(quota);

            final PrintLog logEntry = new PrintLog();
            logEntry.setUser(saved);
            logEntry.setTimestamp(Instant.now());
            logEntry.setDocumentName("budget.pdf");
            logEntry.setPrinterName("Finance_Dept");
            logEntry.setPageCount(10);
            logEntry.setStatus(PrintStatus.SUCCESS);
            logEntry.setCorrelationId("corr-777");
            printLogRepository.saveAndFlush(logEntry);

            return saved;
        });
    }

    @Test
    void testSearchUsersEndpoint() {
        final ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/admin/users?username=adminuser", String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("company\\\\adminuser").contains("Finance");
    }

    @Test
    void testGetDashboardSummaryEndpoint() {
        final ResponseEntity<DashboardSummary> response = restTemplate.getForEntity(
                "/api/v1/admin/dashboard", DashboardSummary.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        final DashboardSummary summary = response.getBody();
        assertThat(summary).isNotNull();
        assertThat(summary.getTotalUsers()).isEqualTo(1);
        assertThat(summary.getMonthlyPagesPrinted()).isEqualTo(50);
        assertThat(summary.getPagesRemaining()).isEqualTo(150);
    }

    @Test
    void testAdjustQuotaEndpoint() {
        final com.printkeep.quota.core.admin.dto.QuotaAdjustmentRequest request =
                new com.printkeep.quota.core.admin.dto.QuotaAdjustmentRequest(50, "Manager approved override", "admin-mgr");

        final ResponseEntity<Quota> response = restTemplate.postForEntity(
                "/api/v1/admin/quotas/" + savedUser.getId() + "/adjust",
                request,
                Quota.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getAllocatedPages()).isEqualTo(250);

        // Verify the QuotaAdjustmentLog entry was persisted
        final List<com.printkeep.quota.core.model.QuotaAdjustmentLog> logs = adjustmentLogRepository.findAll();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getAdjustmentAmount()).isEqualTo(50);
        assertThat(logs.get(0).getReason()).isEqualTo("Manager approved override");
        assertThat(logs.get(0).getApprovedBy()).isEqualTo("admin-mgr");
    }

    @Test
    void testDisableUserImmediateEndpointAndPrintRejection() {
        // 1. Double check the user is initially active
        assertThat(savedUser.isActive()).isTrue();

        // 2. Call disable-immediate endpoint
        final ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/admin/users/" + savedUser.getId() + "/disable-immediate",
                null,
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("SUCCESS");

        // 3. Reload from repository to assert state change is immediate
        final User updatedUser = userRepository.findById(savedUser.getId()).orElseThrow();
        assertThat(updatedUser.isActive()).isFalse();

        // 4. Verify that subsequent print evaluation is blocked by the pipeline
        // Construct a mock IPP packet containing the username in operation attributes
        final com.printkeep.quota.codec.model.IppAttribute usernameAttr =
                new com.printkeep.quota.codec.model.IppAttribute(
                        "requesting-user-name",
                        com.printkeep.quota.codec.model.IppTag.NAME_WITHOUT_LANGUAGE,
                        List.of("company\\adminuser")
                );
        final com.printkeep.quota.codec.model.IppAttributeGroup opGroup =
                new com.printkeep.quota.codec.model.IppAttributeGroup(
                        com.printkeep.quota.codec.model.IppTag.OPERATION_ATTRIBUTES,
                        List.of(usernameAttr)
                );
        final com.printkeep.quota.codec.model.IppPacket packet =
                new com.printkeep.quota.codec.model.IppPacket((byte) 2, (byte) 0, (short) 0x0002, 1, List.of(opGroup), new byte[0]);

        final com.printkeep.quota.core.processing.pipeline.PipelineResult result =
                pipeline.process(packet, "corr-disable-test", "127.0.0.1", "company\\adminuser");

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.decision()).isEqualTo(com.printkeep.quota.core.processing.decision.IppDecision.REJECT_DISABLED_USER);
    }

    @Test
    void testExcelExportGeneration() throws Exception {
        final List<PrintLog> logs = printLogRepository.findAll();
        try (final ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            exportService.exportPrintLogsToExcel(logs, out);
            final byte[] excelBytes = out.toByteArray();
            assertThat(excelBytes).isNotEmpty();
            // Verify that the file header matches a ZIP block (valid OOXML magic bytes: PK..)
            assertThat(excelBytes[0]).isEqualTo((byte) 'P');
            assertThat(excelBytes[1]).isEqualTo((byte) 'K');
        }
    }
}
