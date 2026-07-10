package com.printkeep.quota.core.identity.ldap.health;

import com.printkeep.quota.core.identity.ldap.config.LdapProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Health indicator monitoring Active Directory / LDAP connection status and response times.
 */
@Component
public class LdapHealthIndicator implements HealthIndicator {

    private final LdapTemplate ldapTemplate;
    private final LdapProperties properties;

    public LdapHealthIndicator(final LdapTemplate ldapTemplate, final LdapProperties properties) {
        this.ldapTemplate = ldapTemplate;
        this.properties = properties;
    }

    @Override
    public Health health() {
        final Instant start = Instant.now();
        try {
            // Perform a minimal query against the configured search base to check connection liveness.
            // Limits results to 1 to minimize connection and processing overhead.
            ldapTemplate.search(
                    properties.getSearchBase(),
                    "(objectClass=*)",
                    1,
                    attrs -> null
            );

            final long responseTimeMs = Duration.between(start, Instant.now()).toMillis();
            final String maskedUrls = String.join(", ", properties.getUrls()).replaceAll(":[^@/]+@", ":***@");

            if (responseTimeMs > 2000) {
                // If response time is slow (> 2 seconds), report as DEGRADED.
                return Health.status("DEGRADED")
                        .withDetail("message", "LDAP server response latency is high")
                        .withDetail("responseTimeMs", responseTimeMs)
                        .withDetail("urls", maskedUrls)
                        .withDetail("searchBase", properties.getSearchBase())
                        .build();
            }

            return Health.up()
                    .withDetail("responseTimeMs", responseTimeMs)
                    .withDetail("urls", maskedUrls)
                    .withDetail("searchBase", properties.getSearchBase())
                    .build();

        } catch (final Exception e) {
            final long responseTimeMs = Duration.between(start, Instant.now()).toMillis();
            return Health.down()
                    .withDetail("message", "LDAP server communication failed")
                    .withDetail("error", e.getMessage())
                    .withDetail("responseTimeMs", responseTimeMs)
                    .build();
        }
    }
}
