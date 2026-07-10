package com.printkeep.quota.service;

import com.printkeep.quota.model.PrintLog;
import com.printkeep.quota.model.Quota;
import com.printkeep.quota.model.User;
import com.printkeep.quota.repository.PrintLogRepository;
import com.printkeep.quota.repository.QuotaRepository;
import com.printkeep.quota.repository.UserRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrintQuotaService {

    private final UserRepository userRepository;
    private final QuotaRepository quotaRepository;
    private final PrintLogRepository printLogRepository;
    private final LdapService ldapService;

    @Value("${print-quota.default-quota:106}")
    private int defaultQuota;

    @Getter
    @RequiredArgsConstructor
    public static class QuotaCheckResult {
        private final boolean allowed;
        private final String message;
        private final User user;
        private final int calculatedPages;
    }

    /**
     * Core transactional engine to check user quotas, apply page deduction, and log transactions.
     * Pessimistic locking is applied on the Quota row to prevent race conditions during concurrent jobs.
     */
    @Transactional
    public QuotaCheckResult processPrintJobQuota(String username, int jobImpressions, String sides, String documentName) {
        if (username == null || username.trim().isEmpty()) {
            return new QuotaCheckResult(false, "Authentication failure: Missing username in IPP headers", null, 0);
        }

        // Calculate pages using duplex rules
        int calculatedPages = calculatePageCount(jobImpressions, sides);
        String currentMonthYear = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));

        try {
            // 1. Fetch or auto-provision the user
            User user = getOrCreateUser(username, currentMonthYear);

            if (!user.getIsActive()) {
                log.warn("Print job rejected: User {} is inactive.", username);
                logPrintLog(user, documentName, calculatedPages, PrintLog.Status.REJECTED_QUOTA);
                return new QuotaCheckResult(false, "User is marked as inactive", user, calculatedPages);
            }

            // 2. Fetch Quota with PESSIMISTIC_WRITE lock to block concurrent updates on the same user's quota
            Optional<Quota> quotaOpt = quotaRepository.findByUserAndMonthYearForUpdate(user, currentMonthYear);
            Quota quota;
            if (quotaOpt.isPresent()) {
                quota = quotaOpt.get();
            } else {
                // If quota was missing for some reason, provision it
                quota = Quota.builder()
                        .user(user)
                        .monthYear(currentMonthYear)
                        .allocatedPages(defaultQuota)
                        .usedPages(0)
                        .build();
                quota = quotaRepository.saveAndFlush(quota);
            }

            int currentUsed = quota.getUsedPages();
            int limit = quota.getAllocatedPages();

            if (currentUsed + calculatedPages > limit) {
                log.warn("Print job rejected: User {} has insufficient quota. Needed: {}, Available: {}", 
                        username, calculatedPages, (limit - currentUsed));
                logPrintLog(user, documentName, calculatedPages, PrintLog.Status.REJECTED_QUOTA);
                return new QuotaCheckResult(false, "Insufficient print quota. Remaining: " + (limit - currentUsed), user, calculatedPages);
            }

            // 3. Update quota usage and log success
            quota.setUsedPages(currentUsed + calculatedPages);
            quotaRepository.save(quota);
            
            logPrintLog(user, documentName, calculatedPages, PrintLog.Status.SUCCESS);
            log.info("Print job allowed for user {}. Pages: {}, New usage: {}/{}", 
                    username, calculatedPages, quota.getUsedPages(), limit);
            
            return new QuotaCheckResult(true, "Quota approved", user, calculatedPages);

        } catch (Exception e) {
            log.error("Internal error processing print quota check for user: {}", username, e);
            // Log as system error if we have a user
            userRepository.findByDomainUsername(username).ifPresent(u -> 
                logPrintLog(u, documentName, calculatedPages, PrintLog.Status.ERROR)
            );
            return new QuotaCheckResult(false, "Internal system validation error: " + e.getMessage(), null, calculatedPages);
        }
    }

    /**
     * Auto-provisions a user if they do not exist.
     */
    private User getOrCreateUser(String username, String currentMonthYear) {
        return userRepository.findByDomainUsername(username)
                .orElseGet(() -> {
                    log.info("User {} not found in database. Initiating AD auto-provisioning...", username);
                    String department = ldapService.getUserDepartment(username);
                    
                    User newUser = User.builder()
                            .domainUsername(username)
                            .department(department)
                            .isActive(true)
                            .build();
                    
                    newUser = userRepository.saveAndFlush(newUser);
                    
                    // Pre-create quota for the user
                    Quota newQuota = Quota.builder()
                            .user(newUser)
                            .monthYear(currentMonthYear)
                            .allocatedPages(defaultQuota)
                            .usedPages(0)
                            .build();
                    quotaRepository.saveAndFlush(newQuota);
                    
                    log.info("Successfully provisioned user {} with department: {} and default quota: {}", 
                            username, department, defaultQuota);
                    return newUser;
                });
    }

    /**
     * Calculates page count based on duplex rules:
     * If sides is two-sided-long-edge or two-sided-short-edge, effective page cost is jobImpressions * 2.
     */
    public int calculatePageCount(int jobImpressions, String sides) {
        if (sides != null && (sides.equalsIgnoreCase("two-sided-long-edge") || sides.equalsIgnoreCase("two-sided-short-edge"))) {
            return jobImpressions * 2;
        }
        return jobImpressions;
    }

    private void logPrintLog(User user, String documentName, int pageCount, PrintLog.Status status) {
        PrintLog logEntry = PrintLog.builder()
                .user(user)
                .timestamp(LocalDateTime.now())
                .documentName(documentName != null ? documentName : "Unknown Document")
                .pageCount(pageCount)
                .status(status)
                .build();
        printLogRepository.save(logEntry);
    }

    /**
     * Generates quota slots for all active users for a new month.
     */
    @Transactional
    public void rollOverMonthlyQuotas() {
        String nextMonthYear = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        log.info("Starting monthly quota rollover for: {}", nextMonthYear);
        
        List<User> activeUsers = userRepository.findByIsActiveTrue();
        int count = 0;
        for (User user : activeUsers) {
            Optional<Quota> existing = quotaRepository.findByUserAndMonthYear(user, nextMonthYear);
            if (existing.isEmpty()) {
                Quota newQuota = Quota.builder()
                        .user(user)
                        .monthYear(nextMonthYear)
                        .allocatedPages(defaultQuota)
                        .usedPages(0)
                        .build();
                quotaRepository.save(newQuota);
                count++;
            }
        }
        log.info("Completed quota rollover. Allocated {} new quota slots for {}", count, nextMonthYear);
    }
}
