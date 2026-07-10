package com.printkeep.quota.core.processing.pipeline;

import com.printkeep.quota.codec.model.IppPacket;
import com.printkeep.quota.core.model.Quota;
import com.printkeep.quota.core.model.User;
import com.printkeep.quota.core.processing.decision.IppDecision;
import com.printkeep.quota.core.processing.dto.PrintJobMetadata;
import java.time.Instant;

/**
 * Thread-safe payload carrying state through the PrintProcessingPipeline stages.
 */
public class PipelineContext {

    private final IppPacket ippPacket;
    private final String correlationId;
    private final String clientHostname;
    private final Instant startTime;

    private User user;
    private Quota quota;
    private PrintJobMetadata metadata;
    private IppDecision decision;
    private String reason;
    private Throwable exception;

    public PipelineContext(final IppPacket ippPacket, final String correlationId, final String clientHostname) {
        this.ippPacket = ippPacket;
        this.correlationId = correlationId;
        this.clientHostname = clientHostname != null ? clientHostname : "unknown";
        this.startTime = Instant.now();
    }

    public IppPacket getIppPacket() {
        return ippPacket;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getClientHostname() {
        return clientHostname;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public User getUser() {
        return user;
    }

    public void setUser(final User user) {
        this.user = user;
    }

    public Quota getQuota() {
        return quota;
    }

    public void setQuota(final Quota quota) {
        this.quota = quota;
    }

    public PrintJobMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(final PrintJobMetadata metadata) {
        this.metadata = metadata;
    }

    public IppDecision getDecision() {
        return decision;
    }

    public void setDecision(final IppDecision decision) {
        this.decision = decision;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(final String reason) {
        this.reason = reason;
    }

    public Throwable getException() {
        return exception;
    }

    public void setException(final Throwable exception) {
        this.exception = exception;
    }
}
