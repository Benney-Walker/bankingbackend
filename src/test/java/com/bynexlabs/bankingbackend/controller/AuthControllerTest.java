package com.bynexlabs.bankingbackend.controller;

import com.bynexlabs.bankingbackend.Utility.GlobalExceptionHandler;
import com.bynexlabs.bankingbackend.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pins the refresh-token HTTP contract the desktop and mobile apps call (decision D4: POST). */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final String REFRESH_URL = "/api/auth/v1/refresh-token";

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void refreshIsAPostWithTheTokenInTheBody() throws Exception {
        doReturn(ResponseEntity.ok(Map.of("authToken", "new-access-token")))
                .when(authService).refreshAccessToken(any());

        mockMvc.perform(post(REFRESH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authToken").value("new-access-token"));
    }

    @Test
    void refreshNoLongerAcceptsGet() throws Exception {
        mockMvc.perform(get(REFRESH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-token\"}"))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(authService);
    }

    @Test
    void refreshWithoutTokenIsBadRequest() throws Exception {
        mockMvc.perform(post(REFRESH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.refreshToken").value("Refresh token is required"));
        verifyNoInteractions(authService);
    }
}
