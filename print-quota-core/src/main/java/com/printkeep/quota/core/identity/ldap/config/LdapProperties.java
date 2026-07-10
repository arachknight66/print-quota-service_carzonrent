package com.printkeep.quota.core.identity.ldap.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for LDAP Active Directory synchronization.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.ldap")
public class LdapProperties {

    @NotNull(message = "LDAP URLs list must not be null")
    private List<@NotBlank(message = "LDAP URL must not be blank") String> urls;

    @NotBlank(message = "Base DN must not be blank")
    private String baseDn;

    @NotBlank(message = "Bind DN username must not be blank")
    private String username;

    @NotBlank(message = "Bind password must not be blank")
    private String password;

    @NotBlank(message = "Search base (OU) must not be blank")
    private String searchBase = "ou=users";

    @Min(value = 500, message = "Connection timeout must be at least 500ms")
    private int connectionTimeout = 5000;

    @Min(value = 500, message = "Read timeout must be at least 500ms")
    private int readTimeout = 5000;

    private boolean sslEnabled = false;

    private String referral = "ignore";

    @Valid
    private Pool pool = new Pool();

    @Valid
    private Sync sync = new Sync();

    /**
     * Inner class mapping connection pooling parameters.
     */
    @Getter
    @Setter
    public static class Pool {
        @Min(0)
        private int minIdle = 2;

        @Min(1)
        @Max(50)
        private int maxActive = 8;

        private boolean validationOnBorrow = true;
    }

    /**
     * Inner class mapping sync scheduler properties.
     */
    @Getter
    @Setter
    public static class Sync {
        private boolean enabled = false;

        @NotBlank(message = "Sync cron expression must not be blank")
        private String cron = "0 0 * * * *"; // default: hourly
    }
}
