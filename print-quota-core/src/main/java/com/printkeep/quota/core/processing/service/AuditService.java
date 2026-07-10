package com.printkeep.quota.core.processing.service;

import com.printkeep.quota.core.model.PrintLog;
import com.printkeep.quota.core.processing.pipeline.PipelineContext;

/**
 * Service defining audit logging for print requests.
 */
public interface AuditService {

    /**
     * Creates and persists a print job audit log entry based on the context.
     *
     * @param context the print request context.
     * @return the saved PrintLog entity.
     */
    PrintLog logAudit(PipelineContext context);
}
