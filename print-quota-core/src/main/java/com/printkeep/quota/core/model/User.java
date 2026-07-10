package com.printkeep.quota.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Entity mapping representing a User in the Print Quota Management System.
 * Supports soft-delete operations.
 */
@Entity
@Table(name = "users")
@SQLDelete(sql = "UPDATE users SET deleted = true WHERE id = ?")
@SQLRestriction("deleted = false")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank(message = "Domain username must not be blank")
    @Column(name = "domain_username", unique = true, nullable = false)
    private String domainUsername;

    @NotBlank(message = "Department must not be blank")
    @Column(name = "department", nullable = false)
    private String department;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    // Audit Columns
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    /**
     * Pre-persist lifecycle callback to initialize audit details.
     */
    @PrePersist
    protected void onCreate() {
        final Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.createdBy == null) {
            this.createdBy = "system";
        }
        if (this.updatedBy == null) {
            this.updatedBy = "system";
        }
    }

    /**
     * Pre-update lifecycle callback to refresh modification timestamp.
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
        if (this.updatedBy == null) {
            this.updatedBy = "system";
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof User user)) {
            return false;
        }
        // If id is null, compare by domainUsername (business key)
        if (id == null || user.id == null) {
            return Objects.equals(domainUsername, user.domainUsername);
        }
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        if (id == null) {
            return Objects.hashCode(domainUsername);
        }
        return Objects.hashCode(id);
    }
}
