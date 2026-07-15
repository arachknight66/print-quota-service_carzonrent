package com.printkeep.quota.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing an audit log entry for a manual quota adjustment.
 * Records the reason, the authorizing admin, and the correlation identifier.
 */
@Entity
@Table(name = "quota_adjustment_logs")
@Getter
@Setter
@NoArgsConstructor
public class QuotaAdjustmentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull(message = "Quota reference must not be null")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quota_id", nullable = false)
    private Quota quota;

    @Column(name = "adjustment_amount", nullable = false)
    private int adjustmentAmount;

    @NotBlank(message = "Reason must not be blank")
    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @NotBlank(message = "Approved by must not be blank")
    @Column(name = "approved_by", nullable = false, length = 100)
    private String approvedBy;

    @NotNull
    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "correlation_id", length = 50)
    private String correlationId;
}
