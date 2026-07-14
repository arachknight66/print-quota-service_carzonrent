package com.printkeep.quota.core.config;

import com.printkeep.quota.core.identity.ldap.config.LdapProperties;
import com.printkeep.quota.core.security.ClientCertAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.ldap.core.support.BaseLdapPathContextSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.ldap.LdapBindAuthenticationManagerFactory;
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Production security policy backed by enterprise LDAP / Active Directory.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String ADMIN_ROLE = "PRINTKEEP_ADMIN";

    @Bean
    @Profile("prod")
    public SecurityFilterChain securityFilterChain(final HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole(ADMIN_ROLE)
                        .requestMatchers("/actuator/**").hasRole(ADMIN_ROLE)
                        .requestMatchers("/printers/**").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    @Profile("!prod")
    public SecurityFilterChain nonProductionSecurityFilterChain(final HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .build();
    }

    @Bean
    @Profile("prod")
    public FilterRegistrationBean<ClientCertAuthFilter> clientCertAuthFilterRegistration() {
        final FilterRegistrationBean<ClientCertAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ClientCertAuthFilter());
        registration.addUrlPatterns("/printers/*");
        registration.setName("clientCertAuthFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    @Profile("prod")
    public AuthenticationManager authenticationManager(
            final BaseLdapPathContextSource contextSource,
            final LdapProperties ldapProperties,
            @Value("${app.security.ldap.user-search-filter:(sAMAccountName={0})}") final String userSearchFilter,
            @Value("${app.security.ldap.group-search-base:ou=groups}") final String groupSearchBase,
            @Value("${app.security.ldap.group-search-filter:(member={0})}") final String groupSearchFilter) {

        final DefaultLdapAuthoritiesPopulator authoritiesPopulator =
                new DefaultLdapAuthoritiesPopulator(contextSource, groupSearchBase);
        authoritiesPopulator.setGroupSearchFilter(groupSearchFilter);
        authoritiesPopulator.setGroupRoleAttribute("cn");
        authoritiesPopulator.setRolePrefix("ROLE_");
        authoritiesPopulator.setConvertToUpperCase(true);

        final LdapBindAuthenticationManagerFactory factory =
                new LdapBindAuthenticationManagerFactory(contextSource);
        factory.setUserSearchBase(ldapProperties.getSearchBase());
        factory.setUserSearchFilter(userSearchFilter);
        factory.setLdapAuthoritiesPopulator(authoritiesPopulator);
        return factory.createAuthenticationManager();
    }
}
