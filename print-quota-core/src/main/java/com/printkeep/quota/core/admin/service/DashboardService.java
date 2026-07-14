package com.printkeep.quota.core.admin.service;

import com.printkeep.quota.core.admin.dto.DashboardSummary;
import com.printkeep.quota.core.model.PrintStatus;
import com.printkeep.quota.core.repository.PrintLogRepository;
import com.printkeep.quota.core.repository.QuotaRepository;
import com.printkeep.quota.core.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service aggregating BI analytics, logs, and database metrics for dashboard consumption.
 */
@Service
public class DashboardService {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);

    private final UserRepository userRepository;
    private final QuotaRepository quotaRepository;
    private final PrintLogRepository printLogRepository;

    public DashboardService(
            final UserRepository userRepository,
            final QuotaRepository quotaRepository,
            final PrintLogRepository printLogRepository) {
        this.userRepository = userRepository;
        this.quotaRepository = quotaRepository;
        this.printLogRepository = printLogRepository;
    }

    /**
     * Aggregates database metrics into a DashboardSummary instance.
     *
     * @return aggregated dashboard analytics.
     */
    public DashboardSummary getDashboardSummary() {
        final String currentMonth = MONTH_FORMATTER.format(Instant.now());

        final long totalUsers = userRepository.count();
        final long activeUsers = userRepository.countByIsActive(true);
        final long disabledUsers = userRepository.countByIsActive(false);

        final Long totalAllocated = quotaRepository.sumAllocatedPages(currentMonth);
        final Long totalUsed = quotaRepository.sumUsedPages(currentMonth);

        final long allocatedPages = totalAllocated != null ? totalAllocated : 0L;
        final long usedPages = totalUsed != null ? totalUsed : 0L;
        final long remainingPages = Math.max(0L, allocatedPages - usedPages);

        final long allowedJobs = printLogRepository.countByStatus(PrintStatus.SUCCESS);
        final long rejectedJobs = printLogRepository.countByStatus(PrintStatus.REJECTED_QUOTA);

        final Double avgJob = printLogRepository.findAveragePageCount();
        final Integer maxJob = printLogRepository.findMaxPageCount();

        final double averageJobSize = avgJob != null ? avgJob : 0.0;
        final int largestJob = maxJob != null ? maxJob : 0;

        final double quotaUtilization = allocatedPages > 0 
                ? ((double) usedPages / allocatedPages) * 100.0 
                : 0.0;

        // Fetch top 5 active lists
        final Map<String, Long> printers = mapResultList(printLogRepository.findMostActivePrinters(PageRequest.of(0, 5)));
        final Map<String, Long> departments = mapResultList(printLogRepository.findMostActiveDepartments(PageRequest.of(0, 5)));
        final Map<String, Long> users = mapResultList(printLogRepository.findMostActiveUsers(PageRequest.of(0, 5)));

        return DashboardSummary.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .disabledUsers(disabledUsers)
                .monthlyPagesPrinted(usedPages)
                .pagesRemaining(remainingPages)
                .allowedJobs(allowedJobs)
                .rejectedJobs(rejectedJobs)
                .averageJobSize(averageJobSize)
                .largestJob(largestJob)
                .quotaUtilizationPercentage(quotaUtilization)
                .mostActivePrinters(printers)
                .mostActiveDepartments(departments)
                .mostActiveUsers(users)
                .build();
    }

    private Map<String, Long> mapResultList(final List<Object[]> results) {
        final Map<String, Long> map = new LinkedHashMap<>();
        for (final Object[] row : results) {
            if (row[0] != null) {
                map.put(row[0].toString(), (Long) row[1]);
            }
        }
        return map;
    }
}
