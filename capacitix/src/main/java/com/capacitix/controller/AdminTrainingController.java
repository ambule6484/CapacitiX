package com.capacitix.controller;

import com.capacitix.entity.Training;
import com.capacitix.entity.User;
import com.capacitix.service.TrainingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/admin/training")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminTrainingController {

    private final TrainingService trainingService;

    // =========================================================
    // TRAINING LIST
    // GET: /admin/training
    // =========================================================

    @GetMapping
    public String trainingPage(Model model) {

        List<Training> trainings = trainingService.getAllTrainings();

        model.addAttribute("trainings", trainings);

        model.addAttribute(
                "totalTrainings",
                trainingService.getTotalTrainings()
        );

        model.addAttribute(
                "activeTrainings",
                trainingService.getActiveTrainings()
        );

        model.addAttribute(
                "completedTrainings",
                trainingService.getCompletedTrainings()
        );

        model.addAttribute(
                "assignedEmployees",
                trainingService.getAssignedEmployees()
        );

        addAdminDetails(model);

        return "admin/training";
    }

    // =========================================================
    // CREATE TRAINING PAGE
    // GET: /admin/training/create
    // =========================================================

    @GetMapping("/create")
    public String createTrainingPage(Model model) {

        List<User> trainers = trainingService.getAllTrainers();

        model.addAttribute("trainers", trainers);

        addAdminDetails(model);

        return "admin/training-create";
    }

    // =========================================================
    // CREATE TRAINING
    // POST: /admin/training/create
    // =========================================================

    @PostMapping("/create")
    public String createTraining(
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam String category,
            @RequestParam(required = false) Long trainerId,
            @RequestParam String status,
            @RequestParam(
                    required = false,
                    defaultValue = "0"
            ) Integer assignedEmployees,
            @RequestParam(
                    required = false,
                    defaultValue = "0"
            ) Integer progress,
            Model model
    ) {

        try {

            trainingService.createTraining(
                    name,
                    description,
                    category,
                    trainerId,
                    status,
                    assignedEmployees,
                    progress
            );

            return "redirect:/admin/training?success=created";

        } catch (RuntimeException e) {

            // Reload trainer list when validation fails
            List<User> trainers = trainingService.getAllTrainers();

            model.addAttribute("trainers", trainers);

            // Show error message
            model.addAttribute(
                    "error",
                    e.getMessage() != null
                            ? e.getMessage()
                            : "Unable to create training."
            );

            // Preserve entered values
            model.addAttribute("name", name);
            model.addAttribute("description", description);
            model.addAttribute("category", category);
            model.addAttribute("trainerId", trainerId);
            model.addAttribute("status", status);
            model.addAttribute(
                    "assignedEmployees",
                    assignedEmployees
            );
            model.addAttribute("progress", progress);

            addAdminDetails(model);

            return "admin/training-create";
        }
    }

    // =========================================================
    // VIEW TRAINING
    // GET: /admin/training/{id}
    // =========================================================

    @GetMapping("/{id}")
    public String viewTraining(
            @PathVariable Long id,
            Model model
    ) {

        Training training = trainingService.getTraining(id);

        model.addAttribute(
                "training",
                training
        );

        addAdminDetails(model);

        return "admin/training-view";
    }

    // =========================================================
    // ACTIVATE TRAINING
    // POST: /admin/training/{id}/activate
    // =========================================================

    @PostMapping("/{id}/activate")
    public String activateTraining(
            @PathVariable Long id
    ) {

        trainingService.updateStatus(
                id,
                "ACTIVE"
        );

        return "redirect:/admin/training?success=activated";
    }

    // =========================================================
    // COMPLETE TRAINING
    // POST: /admin/training/{id}/complete
    // =========================================================

    @PostMapping("/{id}/complete")
    public String completeTraining(
            @PathVariable Long id
    ) {

        trainingService.updateStatus(
                id,
                "COMPLETED"
        );

        return "redirect:/admin/training?success=completed";
    }

    // =========================================================
    // DELETE TRAINING
    // POST: /admin/training/{id}/delete
    // =========================================================

    @PostMapping("/{id}/delete")
    public String deleteTraining(
            @PathVariable Long id
    ) {

        trainingService.deleteTraining(id);

        return "redirect:/admin/training?success=deleted";
    }

    // =========================================================
    // COMMON ADMIN DETAILS
    // =========================================================

    private void addAdminDetails(Model model) {

        model.addAttribute(
                "userName",
                "Administrator"
        );

        model.addAttribute(
                "userInitial",
                "A"
        );
    }
}