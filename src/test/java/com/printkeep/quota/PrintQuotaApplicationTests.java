package com.printkeep.quota;

import com.printkeep.quota.ipp.IppPacket;
import com.printkeep.quota.model.PrintLog;
import com.printkeep.quota.model.Quota;
import com.printkeep.quota.model.User;
import com.printkeep.quota.repository.PrintLogRepository;
import com.printkeep.quota.repository.QuotaRepository;
import com.printkeep.quota.repository.UserRepository;
import com.printkeep.quota.service.LdapService;
import com.printkeep.quota.service.PrintQuotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "server.ssl.enabled=false", // Disable SSL for standard web context tests
    "print-quota.default-quota=106",
    "print-quota.printer-forward-type=RAW",
    "print-quota.physical-printer-raw-host=localhost",
    "print-quota.physical-printer-raw-port=9100",
    "print-quota.admin-emails=admin@test.local",
    "spring.mail.host=localhost"
})
@ActiveProfiles("test")
public class PrintQuotaApplicationTests {

    @Autowired
    private PrintQuotaService printQuotaService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private QuotaRepository quotaRepository;

    @Autowired
    private PrintLogRepository printLogRepository;

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public LdapService ldapService() {
            return new LdapService() {
                @Override
                public String getUserDepartment(String username) {
                    if ("new.employee".equals(username)) {
                        return "Marketing";
                    }
                    return "Default";
                }
            };
        }
    }

    @Autowired
    private LdapService ldapService;

    @BeforeEach
    public void setup() {
        printLogRepository.deleteAll();
        quotaRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    public void contextLoads() {
        assertNotNull(printQuotaService);
    }

    @Test
    public void testPageCalculationRules() {
        // Simplex long edge (ignored duplex) -> cost is impressions
        int count1 = printQuotaService.calculatePageCount(5, "one-sided");
        assertEquals(5, count1);

        // Duplex short edge -> cost is impressions * 2
        int count2 = printQuotaService.calculatePageCount(5, "two-sided-short-edge");
        assertEquals(10, count2);

        // Duplex long edge -> cost is impressions * 2
        int count3 = printQuotaService.calculatePageCount(12, "two-sided-long-edge");
        assertEquals(24, count3);
    }

    @Test
    public void testQuotaGatekeeperSuccess() {
        // Setup user with 50 pages allocated, 10 used
        User user = User.builder()
                .domainUsername("john.doe")
                .department("HR")
                .isActive(true)
                .build();
        user = userRepository.save(user);

        String currentMonthYear = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        Quota quota = Quota.builder()
                .user(user)
                .monthYear(currentMonthYear)
                .allocatedPages(50)
                .usedPages(10)
                .build();
        quotaRepository.save(quota);

        // Process a simplex print job of 5 pages (5 impressions)
        PrintQuotaService.QuotaCheckResult result = printQuotaService.processPrintJobQuota("john.doe", 5, "one-sided", "resume.pdf");

        assertTrue(result.isAllowed());
        assertEquals(5, result.getCalculatedPages());

        // Verify database is updated
        Quota updatedQuota = quotaRepository.findByUserAndMonthYear(user, currentMonthYear).orElseThrow();
        assertEquals(15, updatedQuota.getUsedPages());

        // Verify log is recorded as SUCCESS
        List<PrintLog> logs = printLogRepository.findAll();
        assertEquals(1, logs.size());
        assertEquals(PrintLog.Status.SUCCESS, logs.get(0).getStatus());
        assertEquals(5, logs.get(0).getPageCount());
    }

    @Test
    public void testQuotaGatekeeperExceeded() {
        // Setup user with 10 pages allocated, 8 used
        User user = User.builder()
                .domainUsername("jane.doe")
                .department("Finance")
                .isActive(true)
                .build();
        user = userRepository.save(user);

        String currentMonthYear = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        Quota quota = Quota.builder()
                .user(user)
                .monthYear(currentMonthYear)
                .allocatedPages(10)
                .usedPages(8)
                .build();
        quotaRepository.save(quota);

        // Print job of 3 pages duplex -> 6 pages cost
        PrintQuotaService.QuotaCheckResult result = printQuotaService.processPrintJobQuota("jane.doe", 3, "two-sided-long-edge", "report.xlsx");

        assertFalse(result.isAllowed());
        assertEquals(6, result.getCalculatedPages());

        // Verify quota is unchanged
        Quota updatedQuota = quotaRepository.findByUserAndMonthYear(user, currentMonthYear).orElseThrow();
        assertEquals(8, updatedQuota.getUsedPages());

        // Verify log is REJECTED_QUOTA
        List<PrintLog> logs = printLogRepository.findAll();
        assertEquals(1, logs.size());
        assertEquals(PrintLog.Status.REJECTED_QUOTA, logs.get(0).getStatus());
    }

    @Test
    public void testLdapAutoProvisioning() {
        // Verify user does not exist
        assertFalse(userRepository.findByDomainUsername("new.employee").isPresent());

        // Request print job for non-existent user
        PrintQuotaService.QuotaCheckResult result = printQuotaService.processPrintJobQuota("new.employee", 10, "one-sided", "doc.pdf");

        assertTrue(result.isAllowed());
        
        // Verify user was provisioned
        Optional<User> userOpt = userRepository.findByDomainUsername("new.employee");
        assertTrue(userOpt.isPresent());
        assertEquals("Marketing", userOpt.get().getDepartment());

        // Verify quota was created
        String currentMonthYear = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        Optional<Quota> quotaOpt = quotaRepository.findByUserAndMonthYear(userOpt.get(), currentMonthYear);
        assertTrue(quotaOpt.isPresent());
        assertEquals(106, quotaOpt.get().getAllocatedPages());
        assertEquals(10, quotaOpt.get().getUsedPages());
    }

    @Test
    public void testIppParser() throws IOException {
        // Construct dummy IPP packet bytes
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeByte(0x02); // Major version
        dos.writeByte(0x00); // Minor version
        dos.writeShort(0x0002); // Print-Job operation
        dos.writeInt(12345); // Request ID

        // Delimiter: operation-attributes-tag
        dos.writeByte(0x01);

        // attribute-charset
        dos.writeByte(0x47); // charset type
        byte[] nameBytes = "attributes-charset".getBytes();
        dos.writeShort(nameBytes.length);
        dos.write(nameBytes);
        byte[] valBytes = "utf-8".getBytes();
        dos.writeShort(valBytes.length);
        dos.write(valBytes);

        // requesting-user-name
        dos.writeByte(0x42); // nameWithoutLanguage type
        byte[] userAttrBytes = "requesting-user-name".getBytes();
        dos.writeShort(userAttrBytes.length);
        dos.write(userAttrBytes);
        byte[] userValBytes = "alice".getBytes();
        dos.writeShort(userValBytes.length);
        dos.write(userValBytes);

        // End attributes
        dos.writeByte(0x03);

        // Raw document trailing content
        dos.write("file content goes here".getBytes());

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        IppPacket packet = IppPacket.parse(bais);

        assertEquals((short) 0x0200, packet.getVersion());
        assertEquals((short) 0x0002, packet.getOperationOrStatus());
        assertEquals(12345, packet.getRequestId());
        assertEquals("utf-8", packet.getSingleStringAttribute("attributes-charset"));
        assertEquals("alice", packet.getSingleStringAttribute("requesting-user-name"));

        // Verify the stream can still be read for remaining file content
        byte[] remaining = bais.readAllBytes();
        assertEquals("file content goes here", new String(remaining));
    }
}
