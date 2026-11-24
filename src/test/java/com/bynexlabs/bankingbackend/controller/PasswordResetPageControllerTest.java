package com.bynexlabs.bankingbackend.controller;

import com.bynexlabs.bankingbackend.DTO.auth.NewPassword;
import com.bynexlabs.bankingbackend.exception.BadRequestException;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.exception.UnauthorizedException;
import com.bynexlabs.bankingbackend.service.PasswordResetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The password reset page (owner decision 2026-11-24) runs the existing reset pipeline step by step. */
@ExtendWith(MockitoExtension.class)
class PasswordResetPageControllerTest {

    @Mock
    private PasswordResetService passwordResetService;

    private PasswordResetPageController controller;
    private Model model;

    @BeforeEach
    void setUp() {
        controller = new PasswordResetPageController(passwordResetService);
        controller.creditUnionName = "Test Credit Union";
        model = new ConcurrentModel();
    }

    // The SMS link fills in the staff ID.
    @Test
    void linkFromTheSmsOpensStepOneWithTheIdFilledIn() {
        assertThat(controller.start("900123", model)).isEqualTo("password-reset");
        assertThat(model.getAttribute("step")).isEqualTo("staffId");
        assertThat(model.getAttribute("staffId")).isEqualTo("900123");
        assertThat(model.getAttribute("creditUnionName")).isEqualTo("Test Credit Union");
    }

    @Test
    void sendingTheCodeMovesToTheCodeStep() {
        controller.requestCode(" 900123 ", model);

        verify(passwordResetService).requestResetCode("900123");
        assertThat(model.getAttribute("step")).isEqualTo("code");
        assertThat(model.getAttribute("error")).isNull();
    }

    @Test
    void unknownStaffIdStaysOnStepOneWithTheReason() {
        doThrow(new BadRequestException("Invalid staff Id or Inactive staff account"))
                .when(passwordResetService).requestResetCode("999999");

        controller.requestCode("999999", model);

        assertThat(model.getAttribute("step")).isEqualTo("staffId");
        assertThat(model.getAttribute("error")).isEqualTo("Invalid staff Id or Inactive staff account");
    }

    // "Send a new code" inside a minute: the pipeline's cooldown message is shown.
    @Test
    void resendWithinAMinuteShowsTheCooldown() {
        doThrow(new ConflictException("Password can only be sent once in a minute"))
                .when(passwordResetService).requestResetCode("900123");

        controller.requestCode("900123", model);

        assertThat(model.getAttribute("error")).isEqualTo("Password can only be sent once in a minute");
    }

    @Test
    void rightCodeGivesTheResetTokenToTheLastStep() {
        when(passwordResetService.confirmResetCode("900123", "123456")).thenReturn("reset-token");

        controller.confirmCode("900123", "123456", model);

        assertThat(model.getAttribute("step")).isEqualTo("password");
        assertThat(model.getAttribute("resetToken")).isEqualTo("reset-token");
    }

    @Test
    void codeMustBeSixDigits() {
        controller.confirmCode("900123", "12ab", model);

        assertThat(model.getAttribute("step")).isEqualTo("code");
        verifyNoInteractions(passwordResetService);
    }

    @Test
    void wrongCodeStaysOnTheCodeStep() {
        when(passwordResetService.confirmResetCode("900123", "000000"))
                .thenThrow(new BadRequestException("Invalid or expired reset code"));

        controller.confirmCode("900123", "000000", model);

        assertThat(model.getAttribute("step")).isEqualTo("code");
        assertThat(model.getAttribute("error")).isEqualTo("Invalid or expired reset code. Check the SMS or send a new code.");
    }

    @Test
    void newPasswordIsSavedThroughThePipeline() {
        controller.newPassword("900123", "reset-token", "long-enough-1", "long-enough-1", model);

        ArgumentCaptor<NewPassword> saved = ArgumentCaptor.forClass(NewPassword.class);
        verify(passwordResetService).saveNewPassword(saved.capture());
        assertThat(saved.getValue().getResetToken()).isEqualTo("reset-token");
        assertThat(saved.getValue().getNewPassword()).isEqualTo("long-enough-1");
        assertThat(model.getAttribute("step")).isEqualTo("done");
    }

    @Test
    void passwordsMustMatchAndBeLongEnough() {
        controller.newPassword("900123", "reset-token", "long-enough-1", "long-enough-2", model);
        assertThat(model.getAttribute("error")).isEqualTo("The two passwords don't match.");

        controller.newPassword("900123", "reset-token", "short", "short", model);
        assertThat(model.getAttribute("error")).isEqualTo("The password must be between 8 and 72 characters.");
        verify(passwordResetService, never()).saveNewPassword(any());
    }

    @Test
    void expiredResetTokenStartsAgain() {
        doThrow(new UnauthorizedException("Invalid reset token")).when(passwordResetService).saveNewPassword(any());

        controller.newPassword("900123", "old-token", "long-enough-1", "long-enough-1", model);

        assertThat(model.getAttribute("step")).isEqualTo("staffId");
        assertThat(model.getAttribute("resetToken")).isNull();
    }

    @Test
    void reusedPasswordIsRefused() {
        doThrow(new ConflictException("Password has already been used before")).when(passwordResetService).saveNewPassword(any());

        controller.newPassword("900123", "reset-token", "long-enough-1", "long-enough-1", model);

        assertThat(model.getAttribute("step")).isEqualTo("password");
        assertThat(model.getAttribute("error")).isEqualTo("Password has already been used before.");
    }

    @Test
    void unexpectedFailureShowsAPlainMessage() {
        doThrow(new IllegalStateException("database down")).when(passwordResetService).requestResetCode(anyString());

        controller.requestCode("900123", model);

        assertThat(model.getAttribute("error")).isEqualTo("Something went wrong. Please try again in a few minutes.");
    }
}
