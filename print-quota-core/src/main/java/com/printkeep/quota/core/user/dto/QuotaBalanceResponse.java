package com.printkeep.quota.core.user.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * DTO returned by the GET /api/v1/me/quota self-service endpoint.
 * Contains the employee's current quota balance for the current billing month.
 */
public record QuotaBalanceResponse(
        String domainUsername,
        String month,
        int allocatedPages,
        int usedPages,
        int remainingPages,
        double quotaUtilizationPercentage
) {
    /**
     * Factory method that constructs a response from raw quota fields and computes the utilization percentage.
     *
     * @param domainUsername  the authenticated employee's AD username.
     * @param month           the billing month in yyyy-MM format.
     * @param allocatedPages  total pages granted for this month.
     * @param usedPages       pages consumed so far this month.
     * @return a fully populated QuotaBalanceResponse.
     */
    public static QuotaBalanceResponse of(
            final String domainUsername,
            final String month,
            final int allocatedPages,
            final int usedPages) {

        final int remaining = Math.max(0, allocatedPages - usedPages);
        final double utilization;
        if (allocatedPages <= 0) {
            utilization = 0.0;
        } else {
            utilization = BigDecimal.valueOf((double) usedPages / allocatedPages * 100.0)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
        }
        return new QuotaBalanceResponse(domainUsername, month, allocatedPages, usedPages, remaining, utilization);
    }
}
