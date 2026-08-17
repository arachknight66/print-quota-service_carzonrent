package com.printkeep.quota.core.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Data Transfer Object encapsulating aggregate BI and operational monitoring data.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummary {

    private long totalUsers;
    private long activeUsers;
    private long disabledUsers;
    private long monthlyPagesPrinted;
    private long pagesRemaining;
    private long rejectedJobs;
    private long allowedJobs;
    private double averageJobSize;
    private int largestJob;
    private double quotaUtilizationPercentage;
    
    private Map<String, Long> mostActivePrinters;
    private Map<String, Long> mostActiveDepartments;
    private Map<String, Long> mostActiveUsers;
}
