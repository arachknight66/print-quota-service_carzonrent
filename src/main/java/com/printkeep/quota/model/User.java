package com.printkeep.quota.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_users_domain_username", columnList = "domain_username", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "employee_id", updatable = false, nullable = false)
    private UUID employeeId;

    @Column(name = "domain_username", nullable = false, unique = true)
    private String domainUsername;

    @Column(name = "department", nullable = false)
    private String department;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;
}
