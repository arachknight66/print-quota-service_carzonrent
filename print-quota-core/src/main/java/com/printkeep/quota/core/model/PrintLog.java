package com.printkeep.quota.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity mapping representing a Print Log transaction entry in the Print Quota Management System.
 */
@Entity
@Table(name = "print_logs")
@Getter
@Setter
@NoArgsConstructor
public class PrintLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull(message = "User must not be null")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private User user;

    @com.fasterxml.jackson.annotation.JsonProperty("domainUsername")
    public String getDomainUsername() {
        if (user == null) return null;
        try {
            return user.getDomainUsername();
        } catch (org.hibernate.LazyInitializationException e) {
            return null;
        }
    }

    @NotNull(message = "Timestamp must not be null")
    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @NotBlank(message = "Document name must not be blank")
    @Column(name = "document_name", nullable = false)
    private String documentName;

    @NotBlank(message = "Printer name must not be blank")
    @Column(name = "printer_name", nullable = false)
    private String printerName;

    @Min(value = 1, message = "Page count must be at least 1")
    @Column(name = "page_count", nullable = false)
    private int pageCount;

    @NotNull(message = "Status must not be null")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PrintStatus status;

    @Column(name = "error_message")
    private String errorMessage;

    @NotBlank(message = "Correlation ID must not be blank")
    @Column(name = "correlation_id", nullable = false)
    private String correlationId;

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
     * Pre-persist lifecycle callback to initialize audit details.
     */
    @PrePersist
    protected void onCreate() {
        final Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.timestamp == null) {
            this.timestamp = now;
        }
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
        if (!(o instanceof PrintLog printLog)) {
            return false;
        }
        // Logs are transactional audit entries, compare by id or business correlation details
        if (id == null || printLog.id == null) {
            return Objects.equals(correlationId, printLog.correlationId) && Objects.equals(timestamp, printLog.timestamp);
        }
        return Objects.equals(id, printLog.id);
    }

    @Override
    public int hashCode() {
        if (id == null) {
            return Objects.hash(correlationId, timestamp);
        }
        return Objects.hashCode(id);
    }
}
