package com.capacitix.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class AuthController {

    // =========================================================
    // LOGIN PAGE
    // URL: /login
    // =========================================================

    @GetMapping("/login")
    public String login(
            @RequestParam(required = false) Boolean error,
            @RequestParam(required = false) Boolean logout,
            @RequestParam(required = false) Boolean disabled,
            @RequestParam(required = false) Boolean passwordSet,
            Model model) {

        /*
         * Login failed
         */
        if (Boolean.TRUE.equals(error)) {

            model.addAttribute(
                    "error",
                    "Invalid email or password."
            );
        }

        /*
         * Logout successful
         */
        if (Boolean.TRUE.equals(logout)) {

            model.addAttribute(
                    "success",
                    "You have been logged out successfully."
            );
        }

        /*
         * Account disabled
         */
        if (Boolean.TRUE.equals(disabled)) {

            model.addAttribute(
                    "error",
                    "Your account is disabled. Please contact the administrator."
            );
        }

        /*
         * Password created successfully.
         *
         * Kept for compatibility with the older
         * trainer invitation flow.
         */
        if (Boolean.TRUE.equals(passwordSet)) {

            model.addAttribute(
                    "success",
                    "Password created successfully. You can now login."
            );
        }

        return "login";
    }

    // =========================================================
    // REGISTER
    // =========================================================

    /*
     * Public registration has been intentionally removed.
     *
     * CapacitiX now follows:
     *
     * ADMIN
     *   ↓
     * Create Employee / Trainer
     *   ↓
     * Email + Password
     *   ↓
     * User can login
     *
     * Therefore /register is redirected to login.
     */

    @GetMapping("/register")
    public String registerPage() {

        return "redirect:/login";
    }
}