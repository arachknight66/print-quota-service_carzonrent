package com.printkeep.quota.core.identity.ldap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.printkeep.quota.core.identity.ldap.config.LdapProperties;
import com.printkeep.quota.core.identity.ldap.dto.LdapUserDto;
import com.printkeep.quota.core.identity.ldap.exception.LdapConnectionException;
import com.printkeep.quota.core.identity.ldap.repository.LdapUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.test.util.ReflectionTestUtils;

import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import java.lang.reflect.Method;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LdapIdentitySimulatorTests {

    @Mock
    private LdapTemplate ldapTemplate;

    @Mock
    private LdapContextSource contextSource;

    @Mock
    private LdapProperties properties;

    @Mock
    private Attributes attributes;

    @Mock
    private Attribute sAMAccountNameAttr;

    @Mock
    private Attribute displayNameAttr;

    @Mock
    private Attribute mailAttr;

    @Mock
    private Attribute departmentAttr;

    @Mock
    private Attribute employeeIDAttr;

    @Mock
    private Attribute userAccountControlAttr;

    @Mock
    private Attribute distinguishedNameAttr;

    private LdapUserRepository ldapUserRepository;

    @BeforeEach
    void setUp() {
        ldapUserRepository = new LdapUserRepository(ldapTemplate, contextSource, properties);
    }

    private String invokeEscape(final String value) {
        try {
            final Method method = LdapUserRepository.class.getDeclaredMethod("escapeLdapSearchValue", String.class);
            method.setAccessible(true);
            return (String) method.invoke(null, value);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testLdapQueryEscaping() {
        // Test escaping rules for LDAP injection defense using reflection
        assertThat(invokeEscape("john*smith")).isEqualTo("john\\2asmith");
        assertThat(invokeEscape("admin(sales)")).isEqualTo("admin\\28sales\\29");
        assertThat(invokeEscape("a\\b")).isEqualTo("a\\5cb");
        assertThat(invokeEscape(null)).isEmpty();
        assertThat(invokeEscape("")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void testFindByUsernameLdapErrorWrapping() {
        when(properties.getSearchBase()).thenReturn("ou=users");
        when(ldapTemplate.search(any(), any(AttributesMapper.class)))
                .thenThrow(new RuntimeException("Active Directory timeout"));

        assertThatThrownBy(() -> ldapUserRepository.findByUsername("jdoe"))
                .isInstanceOf(LdapConnectionException.class)
                .hasMessageContaining("Failed to lookup user in LDAP");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAttributesMapperMappingEnabledUser() throws Exception {
        // Setup mock LDAP attributes
        when(attributes.get("sAMAccountName")).thenReturn(sAMAccountNameAttr);
        when(sAMAccountNameAttr.get()).thenReturn("jdoe");

        when(attributes.get("displayName")).thenReturn(displayNameAttr);
        when(displayNameAttr.get()).thenReturn("John Doe");

        when(attributes.get("mail")).thenReturn(mailAttr);
        when(mailAttr.get()).thenReturn("jdoe@company.local");

        when(attributes.get("department")).thenReturn(departmentAttr);
        when(departmentAttr.get()).thenReturn("Engineering");

        when(attributes.get("employeeID")).thenReturn(employeeIDAttr);
        when(employeeIDAttr.get()).thenReturn("E101");

        when(attributes.get("userAccountControl")).thenReturn(userAccountControlAttr);
        // 512 = NORMAL_ACCOUNT (Enabled)
        when(userAccountControlAttr.get()).thenReturn("512");

        when(attributes.get("distinguishedName")).thenReturn(distinguishedNameAttr);
        when(distinguishedNameAttr.get()).thenReturn("cn=jdoe,ou=users,dc=company,dc=local");

        // Retrieve private AttributesMapper using reflection
        final AttributesMapper<LdapUserDto> mapper =
                (AttributesMapper<LdapUserDto>) ReflectionTestUtils.invokeMethod(ldapUserRepository, "getAttributesMapper");

        final LdapUserDto mapped = mapper.mapFromAttributes(attributes);

        assertThat(mapped.username()).isEqualTo("jdoe");
        assertThat(mapped.displayName()).isEqualTo("John Doe");
        assertThat(mapped.email()).isEqualTo("jdoe@company.local");
        assertThat(mapped.department()).isEqualTo("Engineering");
        assertThat(mapped.employeeId()).isEqualTo("E101");
        assertThat(mapped.enabled()).isTrue();
        assertThat(mapped.distinguishedName()).isEqualTo("cn=jdoe,ou=users,dc=company,dc=local");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAttributesMapperMappingDisabledUser() throws Exception {
        when(attributes.get("sAMAccountName")).thenReturn(sAMAccountNameAttr);
        when(sAMAccountNameAttr.get()).thenReturn("disableduser");

        when(attributes.get("userAccountControl")).thenReturn(userAccountControlAttr);
        // 514 = NORMAL_ACCOUNT | ACCOUNTDISABLE (Disabled: bit 0x02 set)
        when(userAccountControlAttr.get()).thenReturn("514");

        final AttributesMapper<LdapUserDto> mapper =
                (AttributesMapper<LdapUserDto>) ReflectionTestUtils.invokeMethod(ldapUserRepository, "getAttributesMapper");

        final LdapUserDto mapped = mapper.mapFromAttributes(attributes);

        assertThat(mapped.username()).isEqualTo("disableduser");
        assertThat(mapped.enabled()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAttributesMapperFallbackWhenUacMalformed() throws Exception {
        when(attributes.get("sAMAccountName")).thenReturn(sAMAccountNameAttr);
        when(sAMAccountNameAttr.get()).thenReturn("baduac");

        when(attributes.get("userAccountControl")).thenReturn(userAccountControlAttr);
        // Non-numeric string
        when(userAccountControlAttr.get()).thenReturn("malformed_value");

        final AttributesMapper<LdapUserDto> mapper =
                (AttributesMapper<LdapUserDto>) ReflectionTestUtils.invokeMethod(ldapUserRepository, "getAttributesMapper");

        final LdapUserDto mapped = mapper.mapFromAttributes(attributes);

        // Fallback should mark as enabled
        assertThat(mapped.enabled()).isTrue();
    }
}
