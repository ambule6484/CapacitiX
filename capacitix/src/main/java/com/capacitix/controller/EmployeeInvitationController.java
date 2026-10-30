package com.capacitix.controller;

import com.capacitix.entity.EmployeeInvitation;
import com.capacitix.service.EmployeeInvitationService;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class EmployeeInvitationController {

    private final EmployeeInvitationService employeeInvitationService;


    // =========================================================
    // OPEN EMPLOYEE INVITATION LINK
    // =========================================================
    /*
     * Example:
     *
     * http://localhost:8080/employee/accept-invitation?token=xxxx
     *
     * This endpoint MUST remain public.
     *
     * The employee is not logged in yet.
     */

    @GetMapping("/employee/accept-invitation")
    public String acceptInvitationPage(
            @RequestParam(required = false) String token,
            Model model) {

        try {

            // =================================================
            // VALIDATE INVITATION
            // =================================================

            EmployeeInvitation invitation =
                    employeeInvitationService
                            .getValidInvitation(token);


            // =================================================
            // SEND DATA TO THYMELEAF
            // =================================================

            model.addAttribute(
                    "invitation",
                    invitation
            );

            model.addAttribute(
                    "token",
                    token
            );


            // =================================================
            // SHOW CREATE PASSWORD PAGE
            // =================================================

            return "employee/set-password";

        } catch (RuntimeException e) {

            // =================================================
            // SHOW INVITATION ERROR PAGE
            // =================================================

            model.addAttribute(
                    "errorMessage",
                    e.getMessage()
            );

            return "employee/invitation-error";
        }
    }


    // =========================================================
    // CREATE EMPLOYEE PASSWORD
    // =========================================================

    @PostMapping("/employee/accept-invitation")
    public String acceptInvitation(
            @RequestParam(required = false) String token,
            @RequestParam(required = false) String password,
            @RequestParam(required = false) String confirmPassword,
            Model model) {

        try {

            // =================================================
            // ACCEPT INVITATION
            // =================================================

            employeeInvitationService.acceptInvitation(
                    token,
                    password,
                    confirmPassword
            );


            // =================================================
            // SUCCESS
            // =================================================
            /*
             * Employee account is now:
             *
             * enabled = true
             *
             * password = employee's new BCrypt password
             *
             * invitation = used
             *
             * Redirect employee to login.
             */

            return "redirect:/login?invitation=success";


        } catch (RuntimeException e) {

            // =================================================
            // SHOW ERROR ON PASSWORD PAGE
            // =================================================

            model.addAttribute(
                    "errorMessage",
                    e.getMessage()
            );

            model.addAttribute(
                    "token",
                    token
            );


            // =================================================
            // TRY TO LOAD INVITATION AGAIN
            // =================================================

            try {

                EmployeeInvitation invitation =
                        employeeInvitationService
                                .getValidInvitation(token);

                model.addAttribute(
                        "invitation",
                        invitation
                );

            } catch (RuntimeException ignored) {

                /*
                 * Keep the original error message.
                 *
                 * Example:
                 * Passwords do not match.
                 */
            }


            // =================================================
            // RETURN TO PASSWORD PAGE
            // =================================================

            return "employee/set-password";
        }
    }
}