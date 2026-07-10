package com.printkeep.quota.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "print_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrintLog {

    public enum Status {
        SUCCESS,
        REJECTED_QUOTA,
        ERROR
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private User user;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "document_name", length = 512)
    private String documentName;

    @Column(name = "page_count", nullable = false)
    private Integer pageCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;
}
