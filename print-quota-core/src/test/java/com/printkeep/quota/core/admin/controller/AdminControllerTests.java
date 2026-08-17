package com.printkeep.quota.core.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.printkeep.quota.core.admin.dto.QuotaAdjustmentRequest;
import com.printkeep.quota.core.admin.service.AdminQuotaService;
import com.printkeep.quota.core.identity.ldap.service.IdentitySynchronizationService;
import com.printkeep.quota.core.model.Quota;
import com.printkeep.quota.core.model.User;
import com.printkeep.quota.core.repository.PrintLogRepository;
import com.printkeep.quota.core.repository.QuotaRepository;
import com.printkeep.quota.core.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@WebMvcTest(controllers = AdminController.class, properties = "app.admin.ip-filter-enabled=false")
@EnableSpringDataWebSupport
class AdminControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private QuotaRepository quotaRepository;

    @MockBean
    private PrintLogRepository printLogRepository;

    @MockBean
    private IdentitySynchronizationService syncService;

    @MockBean
    private AdminQuotaService adminQuotaService;

    @Test
    @WithMockUser(roles = "PRINTKEEP_ADMIN")
    void testSearchUsers() throws Exception {
        when(userRepository.searchUsers(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("username", "jdoe")
                        .param("department", "Engineering"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "PRINTKEEP_ADMIN")
    void testSearchQuotas() throws Exception {
        when(quotaRepository.searchQuotas(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        mockMvc.perform(get("/api/v1/admin/quotas")
                        .param("username", "jdoe")
                        .param("month", "2026-08"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "PRINTKEEP_ADMIN")
    void testAdjustQuotaSuccess() throws Exception {
        final UUID userId = UUID.randomUUID();
        final QuotaAdjustmentRequest request = new QuotaAdjustmentRequest(150, "Test adjustment", "admin");

        final Quota quota = new Quota();
        quota.setAllocatedPages(150);
        when(adminQuotaService.adjustUserQuota(eq(userId), any(QuotaAdjustmentRequest.class)))
                .thenReturn(quota);

        mockMvc.perform(post("/api/v1/admin/quotas/" + userId + "/adjust")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocatedPages").value(150));
    }

    @Test
    @WithMockUser(roles = "PRINTKEEP_ADMIN")
    void testAdjustQuotaUserNotFound() throws Exception {
        final UUID userId = UUID.randomUUID();
        final QuotaAdjustmentRequest request = new QuotaAdjustmentRequest(150, "Test adjustment", "admin");

        when(adminQuotaService.adjustUserQuota(eq(userId), any(QuotaAdjustmentRequest.class)))
                .thenThrow(new IllegalArgumentException("User not found"));

        mockMvc.perform(post("/api/v1/admin/quotas/" + userId + "/adjust")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("User not found"));
    }

    @Test
    @WithMockUser(roles = "PRINTKEEP_ADMIN")
    void testResetAllQuotas() throws Exception {
        when(quotaRepository.findByMonth(anyString())).thenReturn(Collections.emptyList());

        mockMvc.perform(post("/api/v1/admin/quotas/reset")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("defaultPages", 120))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Successfully reset 0 quotas"));
    }

    @Test
    @WithMockUser(roles = "PRINTKEEP_ADMIN")
    void testTriggerManualLdapSync() throws Exception {
        mockMvc.perform(post("/api/v1/admin/sync")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        verify(syncService, times(1)).synchronizeIdentities();
    }

    @Test
    @WithMockUser(roles = "PRINTKEEP_ADMIN")
    void testDisableUserImmediateSuccess() throws Exception {
        final UUID userId = UUID.randomUUID();
        final User user = new User();
        user.setId(userId);
        user.setActive(true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        mockMvc.perform(post("/api/v1/admin/users/" + userId + "/disable-immediate")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        verify(userRepository, times(1)).save(user);
        verify(userRepository, times(1)).findById(userId);
    }

    @Test
    @WithMockUser(roles = "PRINTKEEP_ADMIN")
    void testDisableUserImmediateNotFound() throws Exception {
        final UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/admin/users/" + userId + "/disable-immediate")
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("User not found"));
    }
}
