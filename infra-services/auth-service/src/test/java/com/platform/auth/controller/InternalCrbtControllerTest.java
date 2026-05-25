package com.platform.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.auth.config.SecurityConfig;
import com.platform.auth.dto.request.CrbtProvisionRequest;
import com.platform.auth.entity.User;
import com.platform.auth.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Field;
import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InternalCrbtController.class)
@Import(SecurityConfig.class)
class InternalCrbtControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private AuthService authService;

    @Test
    @DisplayName("POST /internal/crbt/provision - existing subscriber: 200 with userId/msisdn/roles")
    void provision_existingSubscriber_returns200() throws Exception {
        User user = makeUser(7L, "0901234567", null, "USER");
        when(authService.lazyCreateSubscriber("0901234567")).thenReturn(user);

        mockMvc.perform(post("/internal/crbt/provision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CrbtProvisionRequest("0901234567"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.msisdn").value("0901234567"))
                .andExpect(jsonPath("$.roles[0]").value("USER"));
    }

    @Test
    @DisplayName("POST /internal/crbt/provision - new subscriber: 200 with new userId and USER role")
    void provision_newSubscriber_returns200WithNewUserId() throws Exception {
        User user = makeUser(99L, "0912000001", null, "USER");
        when(authService.lazyCreateSubscriber("0912000001")).thenReturn(user);

        mockMvc.perform(post("/internal/crbt/provision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CrbtProvisionRequest("0912000001"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(99))
                .andExpect(jsonPath("$.msisdn").value("0912000001"))
                .andExpect(jsonPath("$.roles").isArray());
    }

    @Test
    @DisplayName("POST /internal/crbt/provision - response msisdn matches user entity")
    void provision_responseMsisdnMatchesUser() throws Exception {
        User user = makeUser(5L, "0935111222", null, "USER");
        when(authService.lazyCreateSubscriber("0935111222")).thenReturn(user);

        mockMvc.perform(post("/internal/crbt/provision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CrbtProvisionRequest("0935111222"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msisdn").value("0935111222"))
                .andExpect(jsonPath("$.userId").value(5));
    }

    private User makeUser(Long id, String msisdn, String email, String role) {
        try {
            User user = new User(msisdn, email, null, Set.of(role), 2);
            Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, id);
            return user;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to set user id via reflection", e);
        }
    }
}
