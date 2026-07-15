package com.printkeep.quota.core.user.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.printkeep.quota.core.model.Quota;
import com.printkeep.quota.core.model.User;
import com.printkeep.quota.core.repository.QuotaRepository;
import com.printkeep.quota.core.repository.UserRepository;
import com.printkeep.quota.core.security.ClientCertAuthFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Optional;
import java.util.UUID;

/**
 * WebMvcTest slice for MyQuotaController.
 * Tests the three outcomes: valid CN → 200 balance, unknown CN → 404, missing CN attribute → 403.
 */
@WebMvcTest(MyQuotaController.class)
class MyQuotaControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private QuotaRepository quotaRepository;

    private User testUser;
    private Quota testQuota;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setDomainUsername("jdoe");
        testUser.setDepartment("Engineering");
        testUser.setActive(true);

        testQuota = new Quota();
        testQuota.setUser(testUser);
        testQuota.setAllocatedPages(200);
        testQuota.setUsedPages(45);
    }

    /**
     * When a valid verified CN attribute is present and the user/quota records exist,
     * the endpoint must return 200 with a correct QuotaBalanceResponse JSON body.
     */
    @Test
    @WithMockUser
    void testGetMyQuotaBalance_validCn_returns200WithBalance() throws Exception {
        when(userRepository.findByDomainUsername("jdoe")).thenReturn(Optional.of(testUser));
        when(quotaRepository.findByUserIdAndMonth(eq(testUser.getId()), anyString()))
                .thenReturn(Optional.of(testQuota));

        mockMvc.perform(
                get("/api/v1/me/quota")
                        .requestAttr(ClientCertAuthFilter.VERIFIED_CLIENT_CN_ATTRIBUTE, "jdoe")
        )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.domainUsername").value("jdoe"))
                .andExpect(jsonPath("$.allocatedPages").value(200))
                .andExpect(jsonPath("$.usedPages").value(45))
                .andExpect(jsonPath("$.remainingPages").value(155))
                .andExpect(jsonPath("$.quotaUtilizationPercentage").value(22.5));
    }

    /**
     * When the CN attribute is present but there is no matching user in the database,
     * the endpoint must return 404.
     */
    @Test
    @WithMockUser
    void testGetMyQuotaBalance_unknownCn_returns404() throws Exception {
        when(userRepository.findByDomainUsername("ghost")).thenReturn(Optional.empty());

        mockMvc.perform(
                get("/api/v1/me/quota")
                        .requestAttr(ClientCertAuthFilter.VERIFIED_CLIENT_CN_ATTRIBUTE, "ghost")
        )
                .andExpect(status().isNotFound());
    }

    /**
     * When the CN attribute is not present (mTLS filter bypassed, e.g. in dev or test),
     * the endpoint must return 403 Forbidden.
     */
    @Test
    @WithMockUser
    void testGetMyQuotaBalance_missingCnAttribute_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/me/quota"))
                .andExpect(status().isForbidden());
    }

    /**
     * When the user record exists but has no quota allocation for the current month,
     * the endpoint must return 404.
     */
    @Test
    @WithMockUser
    void testGetMyQuotaBalance_noQuotaRecord_returns404() throws Exception {
        when(userRepository.findByDomainUsername("jdoe")).thenReturn(Optional.of(testUser));
        when(quotaRepository.findByUserIdAndMonth(eq(testUser.getId()), anyString()))
                .thenReturn(Optional.empty());

        mockMvc.perform(
                get("/api/v1/me/quota")
                        .requestAttr(ClientCertAuthFilter.VERIFIED_CLIENT_CN_ATTRIBUTE, "jdoe")
        )
                .andExpect(status().isNotFound());
    }
}
