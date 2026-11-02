package com.capacitix.controller;

import com.capacitix.service.PasswordResetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class ForgotPasswordController {

    private final PasswordResetService passwordResetService;

    // =========================================================
    // STEP 1: SHOW FORGOT PASSWORD PAGE
    // =========================================================

    @GetMapping("/forgot-password")
    public String showForgotPasswordPage() {
        return "forgot-password";
    }

    // =========================================================
    // STEP 2: SEND OTP TO EMAIL
    // =========================================================

    @PostMapping("/forgot-password")
    public String sendOtp(
            @RequestParam("email") String email,
            Model model) {

        email = email.trim().toLowerCase();

        try {

            boolean sent = passwordResetService.sendOtp(email);

            if (sent) {

                // Store email for the next page
                model.addAttribute("email", email);

                return "verify-otp";
            }

            // Do not reveal whether email exists
            model.addAttribute(
                    "message",
                    "If an account exists with this email, an OTP has been sent."
            );

            return "forgot-password";

        } catch (Exception e) {

            model.addAttribute(
                    "error",
                    "Unable to send OTP. Please try again."
            );

            return "forgot-password";
        }
    }

    // =========================================================
    // STEP 3: SHOW OTP VERIFICATION PAGE
    // =========================================================

    @GetMapping("/verify-otp")
    public String showVerifyOtpPage(
            @RequestParam(value = "email", required = false) String email,
            Model model) {

        if (email == null || email.trim().isEmpty()) {
            return "redirect:/forgot-password";
        }

        model.addAttribute("email", email.trim().toLowerCase());

        return "verify-otp";
    }

    // =========================================================
    // STEP 4: VERIFY OTP
    // =========================================================

    @PostMapping("/verify-otp")
    public String verifyOtp(
            @RequestParam("email") String email,
            @RequestParam("otp") String otp,
            Model model) {

        email = email.trim().toLowerCase();
        otp = otp.trim();

        try {

            boolean valid = passwordResetService.verifyOtp(email, otp);

            if (valid) {

                /*
                 * OTP is valid.
                 * Move user to the new password page.
                 */
                model.addAttribute("email", email);

                return "reset-password";
            }

            model.addAttribute("email", email);
            model.addAttribute(
                    "error",
                    "Invalid or expired OTP. Please try again."
            );

            return "verify-otp";

        } catch (Exception e) {

            model.addAttribute("email", email);
            model.addAttribute(
                    "error",
                    "Unable to verify OTP. Please try again."
            );

            return "verify-otp";
        }
    }

    // =========================================================
    // STEP 5: SHOW RESET PASSWORD PAGE
    // =========================================================

    @GetMapping("/reset-password")
    public String showResetPasswordPage(
            @RequestParam(value = "email", required = false) String email,
            Model model) {

        if (email == null || email.trim().isEmpty()) {
            return "redirect:/forgot-password";
        }

        model.addAttribute("email", email.trim().toLowerCase());

        return "reset-password";
    }

    // =========================================================
    // STEP 6: SAVE NEW PASSWORD
    // =========================================================

    @PostMapping("/reset-password")
    public String resetPassword(
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            @RequestParam("confirmPassword") String confirmPassword,
            Model model) {

        email = email.trim().toLowerCase();

        // -----------------------------------------------------
        // Check password confirmation
        // -----------------------------------------------------

        if (!password.equals(confirmPassword)) {

            model.addAttribute("email", email);
            model.addAttribute(
                    "error",
                    "Passwords do not match."
            );

            return "reset-password";
        }

        // -----------------------------------------------------
        // Basic password validation
        // -----------------------------------------------------

        if (password.length() < 8) {

            model.addAttribute("email", email);
            model.addAttribute(
                    "error",
                    "Password must contain at least 8 characters."
            );

            return "reset-password";
        }

        try {

            boolean updated =
                    passwordResetService.resetPassword(email, password);

            if (updated) {

                return "redirect:/login?passwordReset=true";
            }

            model.addAttribute("email", email);
            model.addAttribute(
                    "error",
                    "Unable to reset password. Please try again."
            );

            return "reset-password";

        } catch (Exception e) {

            model.addAttribute("email", email);
            model.addAttribute(
                    "error",
                    "Something went wrong while resetting your password."
            );

            return "reset-password";
        }
    }
}