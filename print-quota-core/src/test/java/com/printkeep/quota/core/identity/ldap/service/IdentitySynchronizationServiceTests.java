package com.printkeep.quota.core.identity.ldap.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.printkeep.quota.core.identity.ldap.dto.LdapUserDto;
import com.printkeep.quota.core.identity.ldap.exception.LdapConnectionException;
import com.printkeep.quota.core.identity.ldap.exception.LdapSynchronizationException;
import com.printkeep.quota.core.identity.ldap.mapper.LdapUserMapper;
import com.printkeep.quota.core.identity.ldap.repository.LdapUserRepository;
import com.printkeep.quota.core.model.User;
import com.printkeep.quota.core.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for IdentitySynchronizationService using Mockito.
 */
@ExtendWith(MockitoExtension.class)
class IdentitySynchronizationServiceTests {

    @Mock
    private LdapUserRepository ldapUserRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LdapUserMapper ldapUserMapper;

    private IdentitySynchronizationService syncService;

    @BeforeEach
    void setUp() {
        syncService = new IdentitySynchronizationService(
                ldapUserRepository, userRepository, ldapUserMapper, new SimpleMeterRegistry()
        );
    }

    @Test
    void testSynchronizeNewUsers() {
        final LdapUserDto dto = new LdapUserDto(
                "newuser", "New User", "new@company.local", "Sales", "E010", true, "cn=newuser,ou=users,dc=company,dc=local"
        );
        final User newUser = new User();
        newUser.setDomainUsername("company\\newuser");

        when(ldapUserRepository.findAllUsers()).thenReturn(List.of(dto));
        when(userRepository.findByDomainUsername(anyString())).thenReturn(Optional.empty());
        when(ldapUserMapper.toEntity(dto)).thenReturn(newUser);

        syncService.synchronizeIdentities();

        verify(ldapUserMapper).toEntity(dto);
        verify(userRepository, times(1)).save(newUser);
        verify(ldapUserMapper, never()).updateEntity(any(), any());
    }

    @Test
    void testSynchronizeExistingUsers() {
        final LdapUserDto dto = new LdapUserDto(
                "existing", "Existing User", "existing@company.local", "HR", "E020", true, "cn=existing"
        );
        final User existingUser = new User();
        existingUser.setDomainUsername("company\\existing");
        existingUser.setDepartment("Finance");

        when(ldapUserRepository.findAllUsers()).thenReturn(List.of(dto));
        when(userRepository.findByDomainUsername(anyString())).thenReturn(Optional.of(existingUser));

        syncService.synchronizeIdentities();

        verify(ldapUserMapper).updateEntity(dto, existingUser);
        verify(userRepository, times(1)).save(existingUser);
        verify(ldapUserMapper, never()).toEntity(any());
    }

    @Test
    void testSynchronizeDisabledUsers() {
        final LdapUserDto dto = new LdapUserDto(
                "disabled", "Disabled User", "disabled@company.local", "IT", "E030", false, "cn=disabled"
        );
        final User existingUser = new User();
        existingUser.setDomainUsername("company\\disabled");
        existingUser.setActive(true);

        when(ldapUserRepository.findAllUsers()).thenReturn(List.of(dto));
        when(userRepository.findByDomainUsername(anyString())).thenReturn(Optional.of(existingUser));

        syncService.synchronizeIdentities();

        verify(ldapUserMapper).updateEntity(dto, existingUser);
        verify(userRepository).save(existingUser);
    }

    @Test
    void testSynchronizeLdapConnectionFailure() {
        when(ldapUserRepository.findAllUsers()).thenThrow(new LdapConnectionException("Connection refused"));

        assertThatThrownBy(() -> syncService.synchronizeIdentities())
                .isInstanceOf(LdapSynchronizationException.class);

        verify(userRepository, never()).save(any());
    }
}
