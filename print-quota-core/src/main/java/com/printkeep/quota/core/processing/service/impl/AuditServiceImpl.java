package com.printkeep.quota.core.processing.service.impl;

import com.printkeep.quota.core.model.PrintLog;
import com.printkeep.quota.core.model.PrintStatus;
import com.printkeep.quota.core.processing.decision.IppDecision;
import com.printkeep.quota.core.processing.pipeline.PipelineContext;
import com.printkeep.quota.core.processing.service.AuditService;
import com.printkeep.quota.core.repository.PrintLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Service implementation for persisting audit trails of print requests.
 * Uses REQUIRES_NEW propagation to ensure audit log rows are saved even if parent transactions rollback.
 */
@Service
public class AuditServiceImpl implements AuditService {

    private final PrintLogRepository printLogRepository;

    public AuditServiceImpl(final PrintLogRepository printLogRepository) {
        this.printLogRepository = printLogRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PrintLog logAudit(final PipelineContext context) {
        final PrintLog log = new PrintLog();
        log.setCorrelationId(context.getCorrelationId());
        log.setTimestamp(Instant.now());

        // Set User details if resolved
        if (context.getUser() != null) {
            log.setUser(context.getUser());
        }

        // Set Job Metadata details if resolved
        if (context.getMetadata() != null) {
            log.setDocumentName(context.getMetadata().documentName());
            log.setPrinterName(context.getMetadata().printerName());
            log.setPageCount(context.getMetadata().estimatedPages());
        } else {
            // Fallback for failed requests before metadata extraction
            log.setDocumentName("Unknown Document");
            log.setPrinterName("Unknown Printer");
            log.setPageCount(1);
        }

        // Map decision to status
        final IppDecision decision = context.getDecision() != null ? context.getDecision() : IppDecision.SYSTEM_ERROR;
        if (decision == IppDecision.ALLOW) {
            log.setStatus(PrintStatus.SUCCESS);
        } else if (decision == IppDecision.REJECT_INSUFFICIENT_QUOTA) {
            log.setStatus(PrintStatus.REJECTED_QUOTA);
            log.setErrorMessage(context.getReason());
        } else {
            log.setStatus(PrintStatus.ERROR);
            log.setErrorMessage(context.getReason() != null ? context.getReason() : "General processing error");
        }

        return printLogRepository.save(log);
    }
}
