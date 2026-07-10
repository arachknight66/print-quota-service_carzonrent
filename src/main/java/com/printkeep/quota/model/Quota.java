package com.printkeep.quota.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "quotas", uniqueConstraints = {
    @UniqueConstraint(name = "uk_user_month_year", columnNames = {"employee_id", "month_year"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Quota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "quota_id")
    private Long quotaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private User user;

    @Column(name = "month_year", nullable = false, length = 7)
    private String monthYear; // e.g. "2026-07"

    @Column(name = "allocated_pages", nullable = false)
    private Integer allocatedPages;

    @Column(name = "used_pages", nullable = false)
    private Integer usedPages;
}
