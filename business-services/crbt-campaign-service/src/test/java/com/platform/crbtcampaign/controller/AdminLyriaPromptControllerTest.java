package com.platform.crbtcampaign.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.security.SecurityUtils;
import com.platform.crbtcampaign.dto.request.UpdateLyriaPromptRequest;
import com.platform.crbtcampaign.dto.response.LyriaPromptResponse;
import com.platform.crbtcampaign.dto.response.LyriaPromptVersionResponse;
import com.platform.crbtcampaign.service.LyriaPromptAdminService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminLyriaPromptController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
class AdminLyriaPromptControllerTest {

    private static final String CLIP = "lyria-3-clip-preview";
    private static final String TEMPLATE = "Template %s %s %s %d %s %s %s %s";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LyriaPromptAdminService adminService;

    @Autowired
    private ObjectMapper objectMapper;

    private MockedStatic<SecurityUtils> securityUtilsMockedStatic;

    @BeforeEach
    void setUp() {
        securityUtilsMockedStatic = Mockito.mockStatic(SecurityUtils.class);
    }

    @AfterEach
    void tearDown() {
        securityUtilsMockedStatic.close();
    }

    private void asAdmin() {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserRoles).thenReturn(List.of("ADMIN"));
    }

    private LyriaPromptResponse sampleResponse() {
        return new LyriaPromptResponse(
                1L, CLIP, 2, TEMPLATE,
                List.of("C major"), List.of("piano"), List.of("fast"), List.of("studio"),
                "ACTIVE", "ADMIN", Instant.now(), Instant.now(), null);
    }

    @Test
    void getActive_notAdmin_returnsForbidden() throws Exception {
        securityUtilsMockedStatic.when(SecurityUtils::getCurrentUserRoles).thenReturn(List.of("USER"));

        mockMvc.perform(get("/campaigns/admin/lyria-prompts/active").param("model", CLIP))
                .andExpect(status().isForbidden());
    }

    @Test
    void getActive_isAdmin_returnsSuccess() throws Exception {
        asAdmin();
        when(adminService.getActive(eq(CLIP))).thenReturn(sampleResponse());

        mockMvc.perform(get("/campaigns/admin/lyria-prompts/active").param("model", CLIP))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.model").value(CLIP))
                .andExpect(jsonPath("$.data.version").value(2));
    }

    @Test
    void getHistory_isAdmin_returnsRows() throws Exception {
        asAdmin();
        when(adminService.listHistory(anyString())).thenReturn(List.of(
                new LyriaPromptVersionResponse(1L, CLIP, 2, "ACTIVE", "ADMIN", Instant.now(), Instant.now(), null)));

        mockMvc.perform(get("/campaigns/admin/lyria-prompts/history").param("model", "ALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));
    }

    @Test
    void saveNewVersion_isAdmin_returnsSuccess() throws Exception {
        asAdmin();
        UpdateLyriaPromptRequest request = new UpdateLyriaPromptRequest(
                CLIP, TEMPLATE, List.of("C major"), List.of("piano"), List.of("fast"), List.of("studio"));
        when(adminService.saveNewVersion(any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/campaigns/admin/lyria-prompts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.promptTemplate").value(TEMPLATE));
    }

    @Test
    void activateVersion_isAdmin_returnsSuccess() throws Exception {
        asAdmin();
        when(adminService.activateVersion(eq(CLIP), anyInt())).thenReturn(sampleResponse());

        mockMvc.perform(put("/campaigns/admin/lyria-prompts/versions/{model}/{version}/activate", CLIP, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }
}
