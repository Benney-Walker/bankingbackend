package com.bynexlabs.bankingbackend.controller;

import com.bynexlabs.bankingbackend.exception.GlobalExceptionHandler;
import com.bynexlabs.bankingbackend.service.AuthService;
import com.bynexlabs.bankingbackend.service.PasswordResetService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pins the public auth HTTP contracts the desktop and mobile apps call: refresh (D4: POST) and password reset. */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final String REFRESH_URL = "/api/auth/v1/refresh-token";

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
    void refreshIsAPostWithTheTokenInTheBody() throws Exception {
        doReturn(ResponseEntity.ok(Map.of("authToken", "new-access-token")))
                .when(authService).refreshStaffAccessToken(any());

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
    void resetRequestAlwaysGivesTheSameAnswer() throws Exception {
        mockMvc.perform(post("/api/auth/v1/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"staffId\":\"900999\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "If the staff ID belongs to an active staff member, a reset code has been sent by SMS."));
        verify(passwordResetService).requestResetCode("900999");
    }

    @Test
    void resetConfirmRejectsShortPasswordBeforeCheckingTheCode() throws Exception {
        mockMvc.perform(post("/api/auth/v1/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"staffId\":\"900100\",\"code\":\"123456\",\"newPassword\":\"short7!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").exists());
        verifyNoInteractions(passwordResetService);
    }

    @Test
    void resetConfirmRejectsCodeThatIsNotSixDigits() throws Exception {
        mockMvc.perform(post("/api/auth/v1/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"staffId\":\"900100\",\"code\":\"12ab\",\"newPassword\":\"long-enough-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").value("Reset code must be 6 digits"));
        verifyNoInteractions(passwordResetService);
    }

    @Test
    void resetConfirmPassesValidRequestToTheService() throws Exception {
        mockMvc.perform(post("/api/auth/v1/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"staffId\":\"900100\",\"code\":\"123456\",\"newPassword\":\"long-enough-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password has been reset"));
        verify(passwordResetService).confirmResetCode("900100", "123456");
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
