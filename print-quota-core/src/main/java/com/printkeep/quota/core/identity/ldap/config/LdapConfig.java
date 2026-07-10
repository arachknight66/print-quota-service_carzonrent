package com.printkeep.quota.core.identity.ldap.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ldap.core.ContextSource;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ldap.pool2.factory.PooledContextSource;
import org.springframework.ldap.pool2.validation.DefaultDirContextValidator;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring configuration class for Active Directory / LDAP connection source,
 * connection pooling, context mapping, and templates.
 */
@Configuration
@EnableCaching
@EnableConfigurationProperties(LdapProperties.class)
public class LdapConfig {

    /**
     * Configures the LDAP Context Source with environment variables, base DN, and credentials.
     *
     * @param properties type-safe configuration parameters.
     * @return contextual connection source.
     */
    @Bean
    public LdapContextSource ldapContextSource(final LdapProperties properties) {
        final LdapContextSource contextSource = new LdapContextSource();
        contextSource.setUrls(properties.getUrls().toArray(new String[0]));
        contextSource.setBase(properties.getBaseDn());
        contextSource.setUserDn(properties.getUsername());
        contextSource.setPassword(properties.getPassword());

        final Map<String, Object> env = new HashMap<>();
        env.put("com.sun.jndi.ldap.connect.timeout", String.valueOf(properties.getConnectionTimeout()));
        env.put("com.sun.jndi.ldap.read.timeout", String.valueOf(properties.getReadTimeout()));

        if (properties.isSslEnabled()) {
            env.put("java.naming.security.protocol", "ssl");
        }

        contextSource.setBaseEnvironmentProperties(env);
        contextSource.setReferral(properties.getReferral());
        return contextSource;
    }

    /**
     * Wraps the context source in a connection pool to optimize reuse under concurrent load.
     *
     * @param ldapContextSource base LDAP context source.
     * @param properties       type-safe configuration parameters.
     * @return pooled context source.
     */
    @Bean
    public ContextSource contextSource(final LdapContextSource ldapContextSource, final LdapProperties properties) {
        final org.springframework.ldap.pool2.factory.PoolConfig poolConfig = new org.springframework.ldap.pool2.factory.PoolConfig();
        poolConfig.setMinIdlePerKey(properties.getPool().getMinIdle());
        poolConfig.setMaxTotal(properties.getPool().getMaxActive());
        poolConfig.setTestOnBorrow(properties.getPool().isValidationOnBorrow());

        final PooledContextSource pooledContextSource = new PooledContextSource(poolConfig);
        pooledContextSource.setContextSource(ldapContextSource);
        pooledContextSource.setDirContextValidator(new DefaultDirContextValidator());
        return pooledContextSource;
    }

    /**
     * Exposes the LdapTemplate bean using the pooled context source.
     *
     * @param contextSource configured ContextSource.
     * @return LdapTemplate instance.
     */
    @Bean
    public LdapTemplate ldapTemplate(final ContextSource contextSource) {
        return new LdapTemplate(contextSource);
    }
}
