package com.printkeep.quota.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity mapping representing a Quota allocation in the Print Quota Management System.
 * Uses optimistic locking to prevent concurrent update anomalies.
 */
@Entity
@Table(
        name = "quotas",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "month"})
)
@Getter
@Setter
@NoArgsConstructor
public class Quota {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull(message = "User must not be null")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull(message = "Month must not be null")
    @Pattern(regexp = "^\\d{4}-\\d{2}$", message = "Month must be in YYYY-MM format")
    @Column(name = "month", nullable = false, length = 7)
    private String month;

    @Min(value = 0, message = "Allocated pages must be non-negative")
    @Column(name = "allocated_pages", nullable = false)
    private int allocatedPages;

    @Min(value = 0, message = "Used pages must be non-negative")
    @Column(name = "used_pages", nullable = false)
    private int usedPages;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    // Audit Columns
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    /**
     * Gets the remaining print balance calculated dynamically.
     * Storing this value in the database is avoided to preserve normal form (3NF) and ensure consistency.
     *
     * @return remaining pages.
     */
    public int getRemainingPages() {
        return Math.max(0, this.allocatedPages - this.usedPages);
    }

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
        if (!(o instanceof Quota quota)) {
            return false;
        }
        // If id is null, compare business key (user id and month)
        if (id == null || quota.id == null) {
            return Objects.equals(user, quota.user) && Objects.equals(month, quota.month);
        }
        return Objects.equals(id, quota.id);
    }

    @Override
    public int hashCode() {
        if (id == null) {
            return Objects.hash(user, month);
        }
        return Objects.hashCode(id);
    }
}
