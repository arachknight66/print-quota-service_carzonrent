package com.printkeep.quota.core.identity.ldap.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.printkeep.quota.core.identity.ldap.config.LdapProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.ldap.CommunicationException;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;

/**
 * Unit tests for LdapHealthIndicator.
 */
@ExtendWith(MockitoExtension.class)
class LdapHealthIndicatorTests {

    @Mock
    private LdapTemplate ldapTemplate;

    @Mock
    private LdapProperties properties;

    private LdapHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        when(properties.getSearchBase()).thenReturn("ou=users");
        when(properties.getUrls()).thenReturn(List.of("ldap://localhost:389"));
        healthIndicator = new LdapHealthIndicator(ldapTemplate, properties);
    }

    @Test
    void testHealthUp() {
        when(ldapTemplate.search(anyString(), anyString(), anyInt(), any(AttributesMapper.class)))
                .thenReturn(List.of());

        final Health health = healthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKey("responseTimeMs");
    }

    @Test
    void testHealthDown() {
        when(ldapTemplate.search(anyString(), anyString(), anyInt(), any(AttributesMapper.class)))
                .thenThrow(new CommunicationException(new RuntimeException("Connection refused")));

        final Health health = healthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
    }
}
