package com.bynexlabs.bankingbackend.controller;

import com.bynexlabs.bankingbackend.DTO.auth.LoginResponse;
import com.bynexlabs.bankingbackend.exception.GlobalExceptionHandler;
import com.bynexlabs.bankingbackend.service.AuthService;
import com.bynexlabs.bankingbackend.service.PasswordResetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the public auth HTTP contracts the desktop and mobile apps call: separate staff and
 * collector login/refresh (refresh is POST, D4) and the three-step password reset.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private PasswordResetService passwordResetService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, passwordResetService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void collectorLoginReturnsTokens() throws Exception {
        when(authService.collectorAuthentication(any())).thenReturn(LoginResponse.builder()
                .staffName("ESI BOATENG").roles(List.of("COLLECTOR")).authToken("access").refreshToken("refresh").build());

        mockMvc.perform(post("/api/auth/v1/collector-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"staffId\":\"900110\",\"password\":\"long-enough-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("COLLECTOR"))
                .andExpect(jsonPath("$.authToken").value("access"));
    }

    @Test
    void loginWithoutPasswordIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/v1/collector-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"staffId\":\"900110\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(authService);
    }

    @Test
    void staffRefreshIsAPostReturningTheToken() throws Exception {
        when(authService.refreshStaffAccessToken(any())).thenReturn("new-access-token");

        mockMvc.perform(post("/api/auth/v1/refresh-staff-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-token\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("new-access-token"));
    }

    @Test
    void collectorRefreshIsAPostReturningTheToken() throws Exception {
        when(authService.refreshCollectorAccessToken(any())).thenReturn("new-collector-token");

        mockMvc.perform(post("/api/auth/v1/refresh-collector-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-token\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("new-collector-token"));
    }

    @Test
    void refreshDoesNotAcceptGet() throws Exception {
        mockMvc.perform(get("/api/auth/v1/refresh-staff-token"))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(authService);
    }

    @Test
    void refreshWithoutTokenIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/v1/refresh-collector-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.refreshToken").value("Refresh token is required"));
        verifyNoInteractions(authService);
    }

    @Test
    void resetCodeRequestSendsTheCode() throws Exception {
        mockMvc.perform(post("/api/auth/v1/password-reset/request-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"staffId\":\"900100\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("A reset code has been sent via SMS."));
        verify(passwordResetService).requestResetCode("900100");
    }

    @Test
    void resetCodeMustBeSixDigits() throws Exception {
        mockMvc.perform(post("/api/auth/v1/password-reset/confirm-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"staffId\":\"900100\",\"code\":\"12ab\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").value("Reset code must be 6 digits"));
        verifyNoInteractions(passwordResetService);
    }

    @Test
    void newPasswordMustBeAtLeastEightCharacters() throws Exception {
        mockMvc.perform(put("/api/auth/v1/password-reset/new-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"short7!\",\"resetToken\":\"token\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").exists());
        verifyNoInteractions(passwordResetService);
    }
}
