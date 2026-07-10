package com.printkeep.quota.core.processing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.printkeep.quota.codec.model.IppPacket;
import com.printkeep.quota.core.model.Quota;
import com.printkeep.quota.core.model.User;
import com.printkeep.quota.core.processing.decision.IppDecision;
import com.printkeep.quota.core.processing.dto.PrintJobMetadata;
import com.printkeep.quota.core.processing.exception.QuotaExceededException;
import com.printkeep.quota.core.processing.pipeline.PipelineContext;
import com.printkeep.quota.core.processing.service.impl.PrintTransactionServiceImpl;
import com.printkeep.quota.core.repository.QuotaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests verifying print reservation transaction management and audit logging behavior.
 */
class PrintTransactionServiceTests {

    private PrintTransactionServiceImpl transactionService;
    private QuotaRepository quotaRepository;
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        quotaRepository = mock(QuotaRepository.class);
        auditService = mock(AuditService.class);
        transactionService = new PrintTransactionServiceImpl(quotaRepository, auditService);
    }

    @Test
    void testSuccessfulPageReservation() throws Exception {
        final User user = new User();
        user.setId(UUID.randomUUID());
        user.setDomainUsername("company\\jdoe");

        final Quota quota = new Quota();
        quota.setUser(user);
        quota.setAllocatedPages(100);
        quota.setUsedPages(40);

        final PrintJobMetadata metadata = new PrintJobMetadata(
                "jdoe", "Doc.pdf", "Printer1", 1, false, false, 10, "Job1", "localhost", Instant.now()
        );

        final IppPacket packet = new IppPacket((byte) 2, (byte) 0, (short) 0x0002, 1, List.of(), new byte[0]);
        final PipelineContext context = new PipelineContext(packet, "corr-123", "localhost");
        context.setUser(user);
        context.setMetadata(metadata);

        when(quotaRepository.findByUserIdAndMonthForUpdate(any(UUID.class), anyString()))
                .thenReturn(Optional.of(quota));

        transactionService.reserveQuota(context);

        assertThat(quota.getUsedPages()).isEqualTo(50); // 40 + 10 = 50
        assertThat(context.getDecision()).isEqualTo(IppDecision.ALLOW);

        verify(quotaRepository, times(1)).save(quota);
        verify(auditService, times(1)).logAudit(context);
    }

    @Test
    void testQuotaExceededRethrowsAndLogsAudit() {
        final User user = new User();
        user.setId(UUID.randomUUID());

        final Quota quota = new Quota();
        quota.setUser(user);
        quota.setAllocatedPages(100);
        quota.setUsedPages(95); // 5 pages remaining

        final PrintJobMetadata metadata = new PrintJobMetadata(
                "jdoe", "Doc.pdf", "Printer1", 1, false, false, 10, "Job1", "localhost", Instant.now()
        );

        final IppPacket packet = new IppPacket((byte) 2, (byte) 0, (short) 0x0002, 1, List.of(), new byte[0]);
        final PipelineContext context = new PipelineContext(packet, "corr-123", "localhost");
        context.setUser(user);
        context.setMetadata(metadata);

        when(quotaRepository.findByUserIdAndMonthForUpdate(any(UUID.class), anyString()))
                .thenReturn(Optional.of(quota));

        assertThatThrownBy(() -> transactionService.reserveQuota(context))
                .isInstanceOf(QuotaExceededException.class);

        assertThat(context.getDecision()).isEqualTo(IppDecision.REJECT_INSUFFICIENT_QUOTA);
        verify(quotaRepository, never()).save(any(Quota.class));
        verify(auditService, times(1)).logAudit(context);
    }
}
