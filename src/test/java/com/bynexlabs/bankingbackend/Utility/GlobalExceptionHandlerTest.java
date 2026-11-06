package com.bynexlabs.bankingbackend.Utility;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("POST", "/api/member/v1/add-member");
    }

    private void assertStandardBody(ResponseEntity<Map<String, Object>> response, int status, String error, Object message) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        Map<String, Object> body = response.getBody();
        assertThat(body).containsKeys("timestamp", "status", "error", "message", "path");
        assertThat(body.get("status")).isEqualTo(status);
        assertThat(body.get("error")).isEqualTo(error);
        assertThat(body.get("message")).isEqualTo(message);
        assertThat(body.get("path")).isEqualTo("/api/member/v1/add-member");
    }

    @Test
    void conflictUsesStandardBodyWithServiceMessage() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleApiException(new ConflictException("Email already exist"), request);

        assertStandardBody(response, 409, "Conflict", "Email already exist");
    }

    @Test
    void badRequestUsesStandardBody() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleApiException(new BadRequestException("Invalid phone number"), request);

        assertStandardBody(response, 400, "Bad Request", "Invalid phone number");
    }

    @Test
    void notFoundUsesStandardBody() {
        ResponseEntity<Map<String, Object>> response = handler.handleApiException(
                new ResourceNotFoundException("Could not find membership record to update"), request);

        assertStandardBody(response, 404, "Not Found", "Could not find membership record to update");
    }

    @Test
    void unauthorizedUsesStandardBody() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleApiException(new UnauthorizedException("Invalid refresh token"), request);

        assertStandardBody(response, 401, "Unauthorized", "Invalid refresh token");
    }

    @Test
    void badCredentialsUsesStandardBody() {
        ResponseEntity<Map<String, Object>> response = handler.handleBadCredentials(request);

        assertStandardBody(response, 401, "Unauthorized", "Invalid ID or password");
    }

    @Test
    void unexpectedErrorsHideInternalDetails() {
        ResponseEntity<Map<String, Object>> response = handler.handleException(
                new IllegalStateException("duplicate key value violates unique constraint \"staffs_email_key\""), request);

        assertStandardBody(response, 500, "Internal Server Error", "Internal Server Error! Contact developer");
        assertThat(response.getBody().toString()).doesNotContain("staffs_email_key");
    }
}
