package com.printkeep.quota.core.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload carrying quota adjustment parameters, reason, and approval trail.
 */
public record QuotaAdjustmentRequest(
        @NotNull(message = "Adjustment amount must not be null")
        Integer adjustment,

        @NotBlank(message = "Reason for adjustment must be specified")
        String reason,

        @NotBlank(message = "Approver username must be specified")
        String approvedBy
) {}
