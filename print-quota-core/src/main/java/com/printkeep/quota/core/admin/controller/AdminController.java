package com.printkeep.quota.core.admin.controller;

import com.printkeep.quota.core.identity.ldap.service.IdentitySynchronizationService;
import com.printkeep.quota.core.model.PrintLog;
import com.printkeep.quota.core.model.PrintStatus;
import com.printkeep.quota.core.model.Quota;
import com.printkeep.quota.core.model.User;
import com.printkeep.quota.core.repository.PrintLogRepository;
import com.printkeep.quota.core.repository.QuotaRepository;
import com.printkeep.quota.core.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Versioned REST API Controller providing admin and operational management controls.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);

    private final UserRepository userRepository;
    private final QuotaRepository quotaRepository;
    private final PrintLogRepository printLogRepository;
    private final IdentitySynchronizationService syncService;
    private final com.printkeep.quota.core.admin.service.AdminQuotaService adminQuotaService;

    public AdminController(
            final UserRepository userRepository,
            final QuotaRepository quotaRepository,
            final PrintLogRepository printLogRepository,
            final IdentitySynchronizationService syncService,
            final com.printkeep.quota.core.admin.service.AdminQuotaService adminQuotaService) {
        this.userRepository = userRepository;
        this.quotaRepository = quotaRepository;
        this.printLogRepository = printLogRepository;
        this.syncService = syncService;
        this.adminQuotaService = adminQuotaService;
    }

    @GetMapping("/users")
    public Page<User> searchUsers(
            @RequestParam(name = "username", required = false) final String username,
            @RequestParam(name = "department", required = false) final String department,
            @PageableDefault(size = 20) final Pageable pageable) {
        return userRepository.searchUsers(username, department, pageable);
    }

    @GetMapping("/quotas")
    public Page<Quota> searchQuotas(
            @RequestParam(name = "username", required = false) final String username,
            @RequestParam(name = "month", required = false) final String month,
            @PageableDefault(size = 20) final Pageable pageable) {
        return quotaRepository.searchQuotas(username, month, pageable);
    }

    @PostMapping("/quotas/{userId}/adjust")
    public ResponseEntity<?> adjustQuota(
            @PathVariable(name = "userId") final UUID userId,
            @jakarta.validation.Valid @RequestBody final com.printkeep.quota.core.admin.dto.QuotaAdjustmentRequest request) {
        try {
            final Quota updated = adminQuotaService.adjustUserQuota(userId, request);
            return ResponseEntity.ok(updated);
        } catch (final IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/quotas/reset")
    public ResponseEntity<?> resetAllQuotas(@RequestBody(required = false) final Map<String, Integer> body) {
        final int defaultPages = (body != null && body.containsKey("defaultPages")) 
                ? body.get("defaultPages") 
                : 100;

        final String currentMonth = MONTH_FORMATTER.format(Instant.now());
        final java.util.List<Quota> quotas = quotaRepository.findByMonth(currentMonth);

        for (final Quota quota : quotas) {
            quota.setAllocatedPages(defaultPages);
            quota.setUsedPages(0);
        }
        quotaRepository.saveAll(quotas);

        return ResponseEntity.ok(Map.of("message", "Successfully reset " + quotas.size() + " quotas"));
    }

    @GetMapping("/history")
    public Page<PrintLog> searchHistory(
            @RequestParam(name = "username", required = false) final String username,
            @RequestParam(name = "department", required = false) final String department,
            @RequestParam(name = "printer", required = false) final String printer,
            @RequestParam(name = "status", required = false) final PrintStatus status,
            @RequestParam(name = "start", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final Instant start,
            @RequestParam(name = "end", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final Instant end,
            @RequestParam(name = "documentName", required = false) final String documentName,
            @RequestParam(name = "correlationId", required = false) final String correlationId,
            @PageableDefault(size = 20) final Pageable pageable) {
        return printLogRepository.searchPrintLogs(
                username, department, printer, status, start, end, documentName, correlationId, pageable
        );
    }

    @PostMapping("/sync")
    public ResponseEntity<?> triggerManualLdapSync() {
        syncService.synchronizeIdentities();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Active Directory sync completed"));
    }

    /**
     * Instantly deactivates a user's print quota eligibility, bypassing the hourly scheduler.
     * Used for immediate processing of same-day terminations.
     *
     * @param userId UUID of the user to deactivate.
     * @return 200 OK on success, or 404 if user not found.
     */
    @PostMapping("/users/{userId}/disable-immediate")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> disableUserImmediate(@PathVariable(name = "userId") final UUID userId) {
        final Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }
        
        final User user = userOpt.get();
        user.setActive(false);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "User has been deactivated immediately"));
    }
}
