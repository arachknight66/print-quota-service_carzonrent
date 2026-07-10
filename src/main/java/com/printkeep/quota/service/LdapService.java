package com.printkeep.quota.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.query.LdapQuery;
import org.springframework.stereotype.Service;
import javax.naming.directory.Attribute;
import java.util.List;
import static org.springframework.ldap.query.LdapQueryBuilder.query;

@Service
@Slf4j
public class LdapService {

    @Autowired(required = false)
    private LdapTemplate ldapTemplate;

    /**
     * Queries Active Directory via LDAPS to retrieve the department attribute for a user.
     * If LDAP is down or the user is not found, it falls back to a default value.
     */
    public String getUserDepartment(String username) {
        if (ldapTemplate == null) {
            log.warn("LdapTemplate is not initialized. Using default department for username: {}", username);
            return "Default";
        }

        try {
            log.info("Querying LDAPS for domain username: {}", username);
            LdapQuery query = query()
                    .where("objectClass").is("user")
                    .and("sAMAccountName").is(username);

            List<String> departments = ldapTemplate.search(
                    query,
                    (AttributesMapper<String>) attrs -> {
                        Attribute attr = attrs.get("department");
                        return attr != null ? (String) attr.get() : "Default";
                    }
            );

            if (departments == null || departments.isEmpty()) {
                log.warn("User {} not found in Active Directory. Provisioning with Default department.", username);
                return "Default";
            }

            String department = departments.get(0);
            log.info("Successfully fetched department '{}' from AD for user: {}", department, username);
            return department;

        } catch (Exception e) {
            log.error("Failed to fetch department from AD for user {} due to network/LDAPS error. Using 'Default'.", username, e);
            return "Default";
        }
    }
}
