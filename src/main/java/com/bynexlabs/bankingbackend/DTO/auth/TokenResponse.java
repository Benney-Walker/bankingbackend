package com.bynexlabs.bankingbackend.DTO.auth;

/**
 * A refresh: a new access token and a new refresh token (owner decision 2026-11-15). The old
 * refresh token no longer works, so the app must store this one. Field names match the login
 * response.
 */
public record TokenResponse(String authToken, String refreshToken) {
}
