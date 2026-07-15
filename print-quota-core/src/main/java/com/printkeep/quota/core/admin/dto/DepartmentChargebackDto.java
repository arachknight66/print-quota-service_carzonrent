package com.printkeep.quota.core.admin.dto;

/**
 * Projection DTO mapping quota stats aggregated by department.
 */
public record DepartmentChargebackDto(
        String department,
        long employeeCount,
        long totalAllocatedPages,
        long totalUsedPages
) {}
