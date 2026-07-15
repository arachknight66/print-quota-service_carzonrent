package com.printkeep.quota.core.user.controller;

import com.printkeep.quota.core.model.Quota;
import com.printkeep.quota.core.model.User;
import com.printkeep.quota.core.repository.QuotaRepository;
import com.printkeep.quota.core.repository.UserRepository;
import com.printkeep.quota.core.security.ClientCertAuthFilter;
import com.printkeep.quota.core.user.dto.QuotaBalanceResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Self-service REST endpoint for employees to query their own print quota balance.
 * Authentication: requires a valid mTLS client certificate (enforced by ClientCertAuthFilter
 * via FilterRegistrationBean registration for /api/v1/me/*).
 */
@RestController
@RequestMapping("/api/v1/me")
public class MyQuotaController {

    private static final Logger log = LoggerFactory.getLogger(MyQuotaController.class);

    private static final DateTimeFormatter MONTH_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);

    private final UserRepository userRepository;
    private final QuotaRepository quotaRepository;

    public MyQuotaController(
            final UserRepository userRepository,
            final QuotaRepository quotaRepository) {
        this.userRepository = userRepository;
        this.quotaRepository = quotaRepository;
    }

    /**
     * Returns the authenticated employee's current quota balance for the current calendar month.
     *
     * <p>The employee is identified via the verified client certificate CN set by
     * {@link ClientCertAuthFilter} as a request attribute. Access without a valid client
     * certificate is blocked before this method is reached.
     *
     * @param request the incoming HTTP request (used to read the verified CN attribute).
     * @return {@code 200 OK} with {@link QuotaBalanceResponse}
     * @throws ResponseStatusException {@code 403} if no verified CN is present on the request.
     * @throws ResponseStatusException {@code 404} if the user or quota record cannot be found.
     */
    @GetMapping("/quota")
    public QuotaBalanceResponse getMyQuotaBalance(final HttpServletRequest request) {
        // ClientCertAuthFilter sets this attribute for all requests on /api/v1/me/*
        final String verifiedCn = (String) request.getAttribute(ClientCertAuthFilter.VERIFIED_CLIENT_CN_ATTRIBUTE);
        if (verifiedCn == null || verifiedCn.isBlank()) {
            log.warn("GET /api/v1/me/quota reached without a verified client certificate CN — rejecting");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "A valid client certificate is required to access this endpoint");
        }

        final User user = userRepository.findByDomainUsername(verifiedCn).orElseThrow(() -> {
            log.warn("GET /api/v1/me/quota: no user record found for CN '{}'", verifiedCn);
            return new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No user account found for the presented certificate identity");
        });

        final String currentMonth = MONTH_FORMATTER.format(java.time.Instant.now());

        final Quota quota = quotaRepository.findByUserIdAndMonth(user.getId(), currentMonth)
                .orElseThrow(() -> {
                    log.warn("GET /api/v1/me/quota: no quota record for user '{}' month '{}'",
                            verifiedCn, currentMonth);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "No quota allocation found for the current billing month: " + currentMonth);
                });

        log.debug("GET /api/v1/me/quota: returning balance for user '{}', month '{}'", verifiedCn, currentMonth);

        return QuotaBalanceResponse.of(
                user.getDomainUsername(),
                currentMonth,
                quota.getAllocatedPages(),
                quota.getUsedPages()
        );
    }
}
