package com.printkeep.quota.core.identity.ldap.mapper;

import com.printkeep.quota.core.identity.ldap.dto.LdapUserDto;
import com.printkeep.quota.core.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Mapper component responsible for converting LDAP user DTO representations
 * into JPA User domain entities.
 */
@Component
public class LdapUserMapper {

    @Value("${app.ldap.domain-prefix:company\\\\}")
    private String domainPrefix;

    /**
     * Maps an LdapUserDto to a new User entity.
     *
     * @param dto the LDAP user data transfer object.
     * @return the populated User entity.
     */
    public User toEntity(final LdapUserDto dto) {
        if (dto == null) {
            return null;
        }
        final User user = new User();
        user.setDomainUsername(mapDomainUsername(dto.username()));
        user.setDepartment(dto.department() != null ? dto.department() : "Default");
        user.setActive(dto.enabled());
        return user;
    }

    /**
     * Updates an existing User entity with attributes from LdapUserDto.
     * Never updates primary keys or creation audit parameters.
     *
     * @param dto  the LDAP user source.
     * @param user the existing User entity target.
     */
    public void updateEntity(final LdapUserDto dto, final User user) {
        if (dto == null || user == null) {
            return;
        }
        // Domain username remains unchanged during updates, as it represents the identity key.
        user.setDepartment(dto.department() != null ? dto.department() : "Default");
        user.setActive(dto.enabled());
    }

    private String mapDomainUsername(final String username) {
        if (username == null) {
            return null;
        }
        if (username.contains("\\")) {
            return username;
        }
        return domainPrefix + username;
    }
}
