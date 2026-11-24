package com.bynexlabs.bankingbackend.controller;

import com.bynexlabs.bankingbackend.DTO.auth.NewPassword;
import com.bynexlabs.bankingbackend.exception.ApiException;
import com.bynexlabs.bankingbackend.service.PasswordResetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The password reset page (owner decision 2026-11-24): a server-rendered page (Thymeleaf,
 * templates/password-reset.html) for staff and collectors, linked from the new-staff SMS with the
 * staff ID filled in. It runs the same pipeline as the API, in the same three steps: send a code by
 * SMS, confirm the code, set the new password (which signs the person out everywhere). It doesn't
 * unlock a locked account; only an administrator does.
 *
 * Nothing is kept on the server between steps: the staff ID and, after the code, the short-lived
 * reset token travel in hidden form fields, as they would through the API.
 */
@Slf4j
@Controller
@RequestMapping("/password-reset")
@RequiredArgsConstructor
public class PasswordResetPageController {

    static final String VIEW = "password-reset";

    private static final String TROUBLE = "Something went wrong. Please try again in a few minutes.";

    private final PasswordResetService passwordResetService;

    @Value("${app.credit-union.name}")
    String creditUnionName;

    @GetMapping
    public String start(@RequestParam(required = false) String staffId, Model model) {
        return step(model, "staffId", staffId, null);
    }

    /** Step 1, and "send a new code" from step 2. */
    @PostMapping("/request-code")
    public String requestCode(@RequestParam(required = false) String staffId, Model model) {
        String id = trim(staffId);
        if (id == null) {
            return step(model, "staffId", null, "Enter your staff ID.");
        }
        try {
            passwordResetService.requestResetCode(id);
        } catch (ApiException e) {
            return step(model, "staffId", id, e.getMessage());
        } catch (RuntimeException e) {
            log.error("Password reset page: sending a code failed", e);
            return step(model, "staffId", id, TROUBLE);
        }
        model.addAttribute("info", "We've sent a 6-digit code by SMS to the phone number on your staff record. "
                + "It's valid for 10 minutes.");
        return step(model, "code", id, null);
    }

    @PostMapping("/confirm-code")
    public String confirmCode(@RequestParam(required = false) String staffId, @RequestParam(required = false) String code,
                              Model model) {
        String id = trim(staffId);
        if (id == null) {
            return step(model, "staffId", null, "Enter your staff ID.");
        }
        String entered = trim(code);
        if (entered == null || !entered.matches("\\d{6}")) {
            return step(model, "code", id, "The code is the 6 digits in the SMS.");
        }
        String resetToken;
        try {
            resetToken = passwordResetService.confirmResetCode(id, entered);
        } catch (ApiException e) {
            return step(model, "code", id, e.getMessage() + ". Check the SMS or send a new code.");
        } catch (RuntimeException e) {
            log.error("Password reset page: confirming a code failed", e);
            return step(model, "code", id, TROUBLE);
        }
        model.addAttribute("resetToken", resetToken);
        return step(model, "password", id, null);
    }

    @PostMapping("/new-password")
    public String newPassword(@RequestParam(required = false) String staffId,
                              @RequestParam(required = false) String resetToken,
                              @RequestParam(required = false) String newPassword,
                              @RequestParam(required = false) String confirmPassword, Model model) {
        String id = trim(staffId);
        if (resetToken == null || resetToken.isBlank()) {
            return step(model, "staffId", id, "Your reset session has ended. Start again.");
        }
        model.addAttribute("resetToken", resetToken);
        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 72) {
            return step(model, "password", id, "The password must be between 8 and 72 characters.");
        }
        if (!newPassword.equals(confirmPassword)) {
            return step(model, "password", id, "The two passwords don't match.");
        }
        try {
            passwordResetService.saveNewPassword(NewPassword.builder().newPassword(newPassword).resetToken(resetToken).build());
        } catch (ApiException e) {
            if (e.getStatus().value() == 401) {
                model.addAttribute("resetToken", null);
                return step(model, "staffId", id, "Your reset session has ended. Start again.");
            }
            return step(model, "password", id, e.getMessage() + ".");
        } catch (RuntimeException e) {
            log.error("Password reset page: saving the new password failed", e);
            return step(model, "password", id, TROUBLE);
        }
        return step(model, "done", id, null);
    }

    private String step(Model model, String step, String staffId, String error) {
        model.addAttribute("step", step);
        model.addAttribute("staffId", staffId);
        model.addAttribute("error", error);
        model.addAttribute("creditUnionName", creditUnionName);
        return VIEW;
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
