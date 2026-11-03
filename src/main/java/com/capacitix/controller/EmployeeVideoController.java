package com.capacitix.controller;

import com.capacitix.entity.EmployeeVideoAssignment;
import com.capacitix.entity.User;
import com.capacitix.repository.UserRepository;
import com.capacitix.service.EmployeeVideoAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/employee/videos")
@RequiredArgsConstructor
@PreAuthorize("hasRole('EMPLOYEE')")
public class EmployeeVideoController {

    private final EmployeeVideoAssignmentService assignmentService;
    private final UserRepository userRepository;

    /**
     * Show all videos assigned to the logged-in employee.
     */
    @GetMapping
    public String myVideos(
            Authentication authentication,
            Model model
    ) {

        User employee = getLoggedInEmployee(authentication);

        List<EmployeeVideoAssignment> assignments =
                assignmentService.getEmployeeVideos(employee);

        model.addAttribute("assignments", assignments);

        return "employee/my-videos";
    }

    /**
     * Open a particular assigned video.
     */
    @GetMapping("/{assignmentId}")
    public String watchVideo(
            @PathVariable Long assignmentId,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes
    ) {

        try {

            User employee = getLoggedInEmployee(authentication);

            EmployeeVideoAssignment assignment =
                    assignmentService.getEmployeeAssignment(
                            assignmentId,
                            employee
                    );

            model.addAttribute("assignment", assignment);
            model.addAttribute("video", assignment.getVideo());

            return "employee/video-player";

        } catch (Exception e) {

            redirectAttributes.addFlashAttribute(
                    "error",
                    e.getMessage()
            );

            return "redirect:/employee/videos";
        }
    }

    /**
     * Update video progress.
     */
    @PostMapping("/{assignmentId}/progress")
    public String updateProgress(
            @PathVariable Long assignmentId,
            @RequestParam("progress") Integer progress,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {

        try {

            User employee = getLoggedInEmployee(authentication);

            assignmentService.updateProgress(
                    assignmentId,
                    employee,
                    progress
            );

            redirectAttributes.addFlashAttribute(
                    "success",
                    "Video progress updated."
            );

        } catch (Exception e) {

            redirectAttributes.addFlashAttribute(
                    "error",
                    e.getMessage()
            );
        }

        return "redirect:/employee/videos/" + assignmentId;
    }

    /**
     * Mark video as completed.
     */
    @PostMapping("/{assignmentId}/complete")
    public String markCompleted(
            @PathVariable Long assignmentId,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {

        try {

            User employee = getLoggedInEmployee(authentication);

            assignmentService.markCompleted(
                    assignmentId,
                    employee
            );

            redirectAttributes.addFlashAttribute(
                    "success",
                    "Training video completed successfully."
            );

        } catch (Exception e) {

            redirectAttributes.addFlashAttribute(
                    "error",
                    e.getMessage()
            );
        }

        return "redirect:/employee/videos/" + assignmentId;
    }

    /**
     * Get logged-in employee.
     */
    private User getLoggedInEmployee(
            Authentication authentication
    ) {

        String email = authentication.getName();

        User employee = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Logged-in employee not found."
                        )
                );

        if (employee.getRole() != User.Role.EMPLOYEE) {
            throw new SecurityException(
                    "Only employees can access training videos."
            );
        }

        return employee;
    }
}