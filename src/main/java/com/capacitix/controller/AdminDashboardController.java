package com.capacitix.controller;

import com.capacitix.entity.AssessmentAssignment;
import com.capacitix.entity.OrganizationSettings;
import com.capacitix.entity.User;
import com.capacitix.entity.User.Role;
import com.capacitix.repository.AssessmentAssignmentRepository;
import com.capacitix.repository.TrainerInvitationRepository;
import com.capacitix.repository.UserRepository;
import com.capacitix.service.EmployeeInvitationService;
import com.capacitix.service.OrganizationSettingsService;
import com.capacitix.service.SkillAnalysisService;
import com.capacitix.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final TrainerInvitationRepository trainerInvitationRepository;
    private final OrganizationSettingsService organizationSettingsService;
    private final EmployeeInvitationService employeeInvitationService;
    private final PasswordEncoder passwordEncoder;

    /*
     * =========================================================
     * ASSESSMENT / SKILL ANALYSIS
     * =========================================================
     *
     * Employee
     *     ↓
     * Latest completed assessment
     *     ↓
     * Assessment responses
     *     ↓
     * SkillAnalysisService
     *     ↓
     * Competency-wise score
     *     ↓
     * Skill gaps
     */
    private final AssessmentAssignmentRepository assessmentAssignmentRepository;
    private final SkillAnalysisService skillAnalysisService;


    // =========================================================
    // DASHBOARD
    // =========================================================

    @GetMapping("/dashboard")
    public String dashboard(Model model) {

        LocalDateTime now = LocalDateTime.now();

        model.addAttribute("userName", "Administrator");
        model.addAttribute("userInitial", "A");

        model.addAttribute(
                "employeeCount",
                userRepository.countByRole(User.Role.EMPLOYEE)
        );

        model.addAttribute(
                "trainerCount",
                userRepository.countByRoleAndEnabled(
                        User.Role.TRAINER,
                        true
                )
        );

        model.addAttribute(
                "pendingInvitationCount",
                trainerInvitationRepository
                        .countByUsedFalseAndExpiresAtAfter(now)
        );

        model.addAttribute("employeesNeedingTraining", 0);
        model.addAttribute("trainingCompletion", 0);

        List<User> employees =
                userRepository.findByRoleOrderByFullNameAsc(
                        User.Role.EMPLOYEE
                );

        List<User> trainers =
                userRepository.findByRoleOrderByFullNameAsc(
                        User.Role.TRAINER
                );

        model.addAttribute("recentEmployees", employees);
        model.addAttribute("recentTrainers", trainers);

        model.addAttribute(
                "pendingInvitations",
                trainerInvitationRepository
                        .findByUsedFalseAndExpiresAtAfterOrderByExpiresAtAsc(now)
        );

        return "admin/dashboard";
    }


    // =========================================================
    // EMPLOYEES
    // =========================================================

    @GetMapping("/employees")
    public String employees(
            @RequestParam(required = false) String success,
            @RequestParam(required = false) String error,
            Model model) {

        List<User> employees =
                userRepository.findByRoleOrderByFullNameAsc(
                        User.Role.EMPLOYEE
                );

        long totalEmployees =
                userRepository.countByRole(
                        User.Role.EMPLOYEE
                );

        long activeEmployees =
                userRepository.countByRoleAndEnabled(
                        User.Role.EMPLOYEE,
                        true
                );

        long disabledEmployees =
                userRepository.countByRoleAndEnabled(
                        User.Role.EMPLOYEE,
                        false
                );

        /*
         * =====================================================
         * EMPLOYEE-SPECIFIC SKILL ANALYSIS
         * =====================================================
         */

        Map<Long, SkillAnalysisService.SkillAnalysisResult>
                employeeSkillAnalysis =
                new HashMap<>();

        for (User employee : employees) {

            List<AssessmentAssignment> completedAssignments =
                    assessmentAssignmentRepository
                            .findByEmployeeAndStatusOrderByAssignedAtDesc(
                                    employee,
                                    "COMPLETED"
                            );

            if (completedAssignments == null
                    || completedAssignments.isEmpty()) {

                continue;
            }

            AssessmentAssignment latestAssignment =
                    completedAssignments.get(0);

            try {

                SkillAnalysisService.SkillAnalysisResult analysis =
                        skillAnalysisService.analyzeAssignment(
                                latestAssignment
                        );

                employeeSkillAnalysis.put(
                        employee.getId(),
                        analysis
                );

            } catch (RuntimeException e) {

                System.err.println(
                        "Unable to analyze assessment for employee "
                                + employee.getId()
                                + ": "
                                + e.getMessage()
                );
            }
        }

        model.addAttribute("employees", employees);
        model.addAttribute(
                "employeeSkillAnalysis",
                employeeSkillAnalysis
        );

        model.addAttribute(
                "employeeCount",
                totalEmployees
        );

        model.addAttribute(
                "activeEmployeeCount",
                activeEmployees
        );

        model.addAttribute(
                "pendingEmployeeCount",
                0
        );

        model.addAttribute(
                "disabledEmployeeCount",
                disabledEmployees
        );

        model.addAttribute(
                "totalEmployees",
                totalEmployees
        );

        model.addAttribute(
                "activeEmployees",
                activeEmployees
        );

        model.addAttribute(
                "disabledEmployees",
                disabledEmployees
        );

        model.addAttribute(
                "pendingInvitations",
                0
        );

        model.addAttribute(
                "userName",
                "Administrator"
        );

        model.addAttribute(
                "userInitial",
                "A"
        );

        switch (success == null ? "" : success) {

            case "employee-created" ->
                    model.addAttribute(
                            "successMessage",
                            "Employee account created successfully."
                    );

            case "employee-invited" ->
                    model.addAttribute(
                            "successMessage",
                            "Employee invitation sent successfully."
                    );

            case "employee-disabled" ->
                    model.addAttribute(
                            "successMessage",
                            "Employee account disabled successfully."
                    );

            case "employee-enabled" ->
                    model.addAttribute(
                            "successMessage",
                            "Employee account enabled successfully."
                    );

            case "employee-updated" ->
                    model.addAttribute(
                            "successMessage",
                            "Employee information updated successfully."
                    );

            case "employee-deleted" ->
                    model.addAttribute(
                            "successMessage",
                            "Employee deleted successfully."
                    );
        }

        if (error != null &&
                !error.trim().isEmpty()) {

            model.addAttribute(
                    "errorMessage",
                    error
            );
        }

        return "admin/employees";
    }


    // =========================================================
    // EMPLOYEE INVITATION PAGE
    // =========================================================

    @GetMapping("/employees/add")
    public String addEmployee() {
        return "admin/invite-employee";
    }


    @GetMapping("/employees/invite")
    public String inviteEmployee() {
        return "admin/invite-employee";
    }


    // =========================================================
    // CREATE EMPLOYEE + SEND INVITATION
    // =========================================================

    @PostMapping({
            "/employees/add",
            "/employees/invite"
    })
    public String createEmployeeAndSendInvitation(

            @RequestParam String name,
            @RequestParam String employeeId,
            @RequestParam String email,

            @RequestParam(required = false)
            String department,

            @RequestParam(required = false)
            String designation,

            @RequestParam(required = false)
            String phone,

            @RequestParam(required = false)
            String message
    ) {

        try {

            if (name == null || name.trim().isEmpty()) {
                return redirectEmployeeError(
                        "Employee name is required."
                );
            }

            if (employeeId == null || employeeId.trim().isEmpty()) {
                return redirectEmployeeError(
                        "Employee ID is required."
                );
            }

            if (email == null || email.trim().isEmpty()) {
                return redirectEmployeeError(
                        "Employee email is required."
                );
            }

            employeeInvitationService.createEmployeeAndSendInvitation(

                    name.trim(),

                    email.trim().toLowerCase(),

                    cleanOptionalValue(department),

                    cleanOptionalValue(designation),

                    employeeId.trim(),

                    cleanOptionalValue(phone),

                    cleanOptionalValue(message)
            );

            return "redirect:/admin/employees?success=employee-invited";

        } catch (RuntimeException e) {

            e.printStackTrace();

            return redirectEmployeeError(
                    e.getMessage()
            );
        }
    }


    // =========================================================
    // EMPLOYEE ERROR REDIRECT
    // =========================================================

    private String redirectEmployeeError(String message) {

        if (message == null || message.trim().isEmpty()) {
            message = "Unable to send employee invitation.";
        }

        String encodedMessage =
                message
                        .replace("&", "%26")
                        .replace("?", "%3F")
                        .replace("=", "%3D")
                        .replace(" ", "%20");

        return "redirect:/admin/employees?error="
                + encodedMessage;
    }


    // =========================================================
    // VIEW EMPLOYEE
    // =========================================================

    @GetMapping("/employees/{id}")
    public String viewEmployee(
            @PathVariable Long id,
            Model model) {

        User employee = userService.getUser(id);

        if (employee.getRole() != User.Role.EMPLOYEE) {
            return "redirect:/admin/employees";
        }

        model.addAttribute("employee", employee);

        return "admin/employees/view";
    }


    // =========================================================
    // EDIT EMPLOYEE
    // =========================================================

    @GetMapping("/employees/{id}/edit")
    public String editEmployee(
            @PathVariable Long id,
            Model model) {

        User employee = userService.getUser(id);

        if (employee.getRole() != User.Role.EMPLOYEE) {
            return "redirect:/admin/employees";
        }

        List<User> trainers =
                userRepository.findByRoleOrderByFullNameAsc(
                        User.Role.TRAINER
                );

        model.addAttribute("employee", employee);
        model.addAttribute("trainers", trainers);

        addAdminDetails(model);

        return "admin/employees/edit";
    }


    // =========================================================
    // UPDATE EMPLOYEE
    // =========================================================

    @PostMapping("/employees/{id}/edit")
    public String updateEmployee(
            @PathVariable Long id,

            @ModelAttribute("employee")
            User updatedEmployee,

            @RequestParam(
                    name = "trainerId",
                    required = false
            )
            Long trainerId,

            Model model) {

        User existingEmployee = userService.getUser(id);

        if (existingEmployee.getRole() != User.Role.EMPLOYEE) {
            return "redirect:/admin/employees";
        }

        try {

            if (updatedEmployee.getFullName() != null
                    && !updatedEmployee.getFullName().trim().isEmpty()) {

                existingEmployee.setFullName(
                        updatedEmployee.getFullName().trim()
                );
            }

            if (updatedEmployee.getEmployeeId() != null
                    && !updatedEmployee.getEmployeeId().trim().isEmpty()) {

                String employeeId =
                        updatedEmployee.getEmployeeId().trim();

                if (!employeeId.equals(
                        existingEmployee.getEmployeeId())
                        && userRepository.existsByEmployeeId(employeeId)) {

                    throw new RuntimeException(
                            "Employee ID already exists."
                    );
                }

                existingEmployee.setEmployeeId(employeeId);
            }

            if (updatedEmployee.getEmail() != null
                    && !updatedEmployee.getEmail().trim().isEmpty()) {

                String email =
                        updatedEmployee.getEmail()
                                .trim()
                                .toLowerCase();

                if (!email.equalsIgnoreCase(
                        existingEmployee.getEmail())
                        && userRepository.existsByEmail(email)) {

                    throw new RuntimeException(
                            "Email address already exists."
                    );
                }

                existingEmployee.setEmail(email);
            }

            existingEmployee.setPhoneNumber(
                    cleanOptionalValue(
                            updatedEmployee.getPhoneNumber()
                    )
            );

            existingEmployee.setDepartment(
                    cleanOptionalValue(
                            updatedEmployee.getDepartment()
                    )
            );

            existingEmployee.setDesignation(
                    cleanOptionalValue(
                            updatedEmployee.getDesignation()
                    )
            );

            // =================================================
            // TRAINER ASSIGNMENT
            // =================================================

            if (trainerId != null) {

                User trainer =
                        userRepository.findById(trainerId)
                                .orElseThrow(
                                        () -> new RuntimeException(
                                                "Selected trainer not found."
                                        )
                                );

                if (trainer.getRole() != User.Role.TRAINER) {
                    throw new RuntimeException(
                            "Selected user is not a trainer."
                    );
                }

                if (!trainer.isEnabled()) {
                    throw new RuntimeException(
                            "Selected trainer is disabled."
                    );
                }

                existingEmployee.setTrainer(trainer);

            } else {

                existingEmployee.setTrainer(null);
            }

            existingEmployee.setEnabled(
                    updatedEmployee.isEnabled()
            );

            existingEmployee.setRole(
                    User.Role.EMPLOYEE
            );

            userRepository.save(existingEmployee);

            return "redirect:/admin/employees?success=employee-updated";

        } catch (RuntimeException e) {

            model.addAttribute(
                    "error",
                    e.getMessage()
            );

            model.addAttribute(
                    "employee",
                    existingEmployee
            );

            List<User> trainers =
                    userRepository.findByRoleOrderByFullNameAsc(
                            User.Role.TRAINER
                    );

            model.addAttribute("trainers", trainers);

            addAdminDetails(model);

            return "admin/employees/edit";
        }
    }


    // =========================================================
    // ENABLE EMPLOYEE
    // =========================================================

    @PostMapping("/employees/{id}/enable")
    public String enableEmployee(
            @PathVariable Long id) {

        User user = userService.getUser(id);

        if (user.getRole() != User.Role.EMPLOYEE) {
            throw new RuntimeException(
                    "Selected user is not an employee."
            );
        }

        userService.enableUser(id);

        return "redirect:/admin/employees?success=employee-enabled";
    }


    // =========================================================
    // DISABLE EMPLOYEE
    // =========================================================

    @PostMapping("/employees/{id}/disable")
    public String disableEmployee(
            @PathVariable Long id) {

        User user = userService.getUser(id);

        if (user.getRole() != User.Role.EMPLOYEE) {
            throw new RuntimeException(
                    "Selected user is not an employee."
            );
        }

        userService.disableUser(id);

        return "redirect:/admin/employees?success=employee-disabled";
    }


    // =========================================================
    // DELETE EMPLOYEE
    // =========================================================

    @PostMapping("/employees/{id}/delete")
    public String deleteEmployee(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes) {

        try {

            User employee =
                    userRepository.findById(id).orElse(null);

            if (employee == null) {

                redirectAttributes.addFlashAttribute(
                        "error",
                        "Employee not found."
                );

                return "redirect:/admin/employees";
            }

            if (employee.getRole() != Role.EMPLOYEE) {

                redirectAttributes.addFlashAttribute(
                        "error",
                        "This user is not an employee."
                );

                return "redirect:/admin/employees";
            }

            userRepository.delete(employee);

            redirectAttributes.addFlashAttribute(
                    "success",
                    "Employee deleted successfully."
            );

        } catch (Exception e) {

            redirectAttributes.addFlashAttribute(
                    "error",
                    "Unable to delete employee. "
                            + e.getMessage()
            );
        }

        return "redirect:/admin/employees";
    }


    // =========================================================
    // TRAINERS
    // =========================================================

    @GetMapping("/trainers")
    public String trainers(
            @RequestParam(required = false) String success,
            @RequestParam(required = false) String invited,
            Model model) {

        List<User> trainers =
                userRepository.findByRoleOrderByFullNameAsc(
                        User.Role.TRAINER
                );

        long totalTrainers =
                userRepository.countByRole(
                        User.Role.TRAINER
                );

        long activeTrainers =
                userRepository.countByRoleAndEnabled(
                        User.Role.TRAINER,
                        true
                );

        long disabledTrainers =
                userRepository.countByRoleAndEnabled(
                        User.Role.TRAINER,
                        false
                );

        long pendingInvitations =
                trainerInvitationRepository
                        .countByUsedFalseAndExpiresAtAfter(
                                LocalDateTime.now()
                        );

        model.addAttribute("trainers", trainers);
        model.addAttribute("totalTrainers", totalTrainers);
        model.addAttribute("activeTrainers", activeTrainers);
        model.addAttribute("disabledTrainers", disabledTrainers);
        model.addAttribute("inactiveTrainers", disabledTrainers);
        model.addAttribute("pendingInvitations", pendingInvitations);
        model.addAttribute("totalCourses", 0);

        model.addAttribute("userName", "Administrator");
        model.addAttribute("userInitial", "A");

        switch (success == null ? "" : success) {

            case "trainer-created" ->
                    model.addAttribute(
                            "successMessage",
                            "Trainer account created successfully."
                    );

            case "trainer-enabled" ->
                    model.addAttribute(
                            "successMessage",
                            "Trainer account enabled successfully."
                    );

            case "trainer-disabled" ->
                    model.addAttribute(
                            "successMessage",
                            "Trainer account disabled successfully."
                    );

            case "trainer-updated" ->
                    model.addAttribute(
                            "successMessage",
                            "Trainer information updated successfully."
                    );

            case "trainer-deleted" ->
                    model.addAttribute(
                            "successMessage",
                            "Trainer deleted successfully."
                    );
        }

        if ("trainer-invited".equals(invited)) {

            model.addAttribute(
                    "successMessage",
                    "Trainer invitation sent successfully."
            );
        }

        return "admin/trainers";
    }


    // =========================================================
    // ADD TRAINER
    // =========================================================

    @GetMapping("/trainers/add")
    public String addTrainer() {
        return "admin/trainers/add";
    }


    // =========================================================
    // CREATE TRAINER
    // =========================================================

    @PostMapping("/trainers/add")
    public String createTrainer(
            @RequestParam String name,
            @RequestParam String trainerId,
            @RequestParam String email,
            @RequestParam String department,
            @RequestParam String expertise,
            @RequestParam(required = false) String phone,
            @RequestParam String password,
            Model model) {

        try {

            if (name == null || name.trim().isEmpty()) {
                throw new RuntimeException(
                        "Trainer name is required."
                );
            }

            if (trainerId == null || trainerId.trim().isEmpty()) {
                throw new RuntimeException(
                        "Trainer ID is required."
                );
            }

            if (email == null || email.trim().isEmpty()) {
                throw new RuntimeException(
                        "Trainer email is required."
                );
            }

            if (department == null || department.trim().isEmpty()) {
                throw new RuntimeException(
                        "Department is required."
                );
            }

            if (expertise == null || expertise.trim().isEmpty()) {
                throw new RuntimeException(
                        "Area of expertise is required."
                );
            }

            if (password == null || password.trim().isEmpty()) {
                throw new RuntimeException(
                        "Password is required."
                );
            }

            if (password.trim().length() < 6) {
                throw new RuntimeException(
                        "Password must contain at least 6 characters."
                );
            }

            String normalizedEmail =
                    email.trim().toLowerCase();

            if (userRepository.existsByEmail(normalizedEmail)) {
                throw new RuntimeException(
                        "Email address already exists."
                );
            }

            if (userRepository.existsByTrainerId(
                    trainerId.trim()
            )) {
                throw new RuntimeException(
                        "Trainer ID already exists."
                );
            }

            User trainer = User.builder()
                    .fullName(name.trim())
                    .trainerId(trainerId.trim())
                    .email(normalizedEmail)
                    .department(department.trim())
                    .expertise(expertise.trim())
                    .phoneNumber(cleanOptionalValue(phone))
                    .designation("Trainer")
                    .password(password)
                    .role(User.Role.TRAINER)
                    .enabled(true)
                    .build();

            userService.createTrainer(trainer);

            return "redirect:/admin/trainers?success=trainer-created";

        } catch (RuntimeException e) {

            model.addAttribute("error", e.getMessage());
            model.addAttribute("name", name);
            model.addAttribute("trainerId", trainerId);
            model.addAttribute("email", email);
            model.addAttribute("department", department);
            model.addAttribute("expertise", expertise);
            model.addAttribute("phone", phone);

            return "admin/trainers/add";
        }
    }


    // =========================================================
    // VIEW TRAINER
    // =========================================================

    @GetMapping("/trainers/{id}")
    public String viewTrainer(
            @PathVariable Long id,
            Model model) {

        User trainer =
                userRepository.findById(id).orElse(null);

        if (trainer == null ||
                trainer.getRole() != User.Role.TRAINER) {

            return "redirect:/admin/trainers";
        }

        model.addAttribute("trainer", trainer);

        List<User> assignedEmployees =
                userRepository.findByTrainerAndRoleOrderByFullNameAsc(
                        trainer,
                        User.Role.EMPLOYEE
                );

        model.addAttribute(
                "assignedEmployees",
                assignedEmployees
        );

        addAdminDetails(model);

        return "admin/trainer-view";
    }


    // =========================================================
    // EDIT TRAINER PAGE
    // =========================================================

    @GetMapping("/trainers/{id}/edit")
    public String editTrainer(
            @PathVariable Long id,
            Model model) {

        User trainer =
                userRepository.findById(id).orElse(null);

        if (trainer == null ||
                trainer.getRole() != User.Role.TRAINER) {

            return "redirect:/admin/trainers";
        }

        model.addAttribute("trainer", trainer);

        addAdminDetails(model);

        return "admin/trainer-edit";
    }


    // =========================================================
    // UPDATE TRAINER
    // =========================================================

    @PostMapping("/trainers/{id}/edit")
    public String updateTrainer(
            @PathVariable Long id,
            @RequestParam String fullName,
            @RequestParam String email,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam String trainerId,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String designation,
            @RequestParam(required = false) String expertise,
            @RequestParam(required = false) String password,
            Model model) {

        User trainer =
                userRepository.findById(id).orElse(null);

        if (trainer == null ||
                trainer.getRole() != User.Role.TRAINER) {

            return "redirect:/admin/trainers";
        }

        try {

            if (fullName == null || fullName.trim().isEmpty()) {
                throw new RuntimeException(
                        "Trainer name is required."
                );
            }

            if (email == null || email.trim().isEmpty()) {
                throw new RuntimeException(
                        "Trainer email is required."
                );
            }

            email = email.trim().toLowerCase();

            if (!email.equalsIgnoreCase(trainer.getEmail())
                    && userRepository.existsByEmail(email)) {

                throw new RuntimeException(
                        "Email address already exists."
                );
            }

            if (trainerId == null || trainerId.trim().isEmpty()) {
                throw new RuntimeException(
                        "Trainer ID is required."
                );
            }

            trainerId = trainerId.trim();

            if (!trainerId.equals(trainer.getTrainerId())
                    && userRepository.existsByTrainerId(trainerId)) {

                throw new RuntimeException(
                        "Trainer ID already exists."
                );
            }

            trainer.setFullName(fullName.trim());
            trainer.setEmail(email);

            trainer.setPhoneNumber(
                    cleanOptionalValue(phoneNumber)
            );

            trainer.setTrainerId(trainerId);

            trainer.setDepartment(
                    cleanOptionalValue(department)
            );

            trainer.setDesignation(
                    cleanOptionalValue(designation)
            );

            trainer.setExpertise(
                    cleanOptionalValue(expertise)
            );

            if (password != null &&
                    !password.trim().isEmpty()) {

                String newPassword =
                        password.trim();

                if (newPassword.length() < 6) {
                    throw new RuntimeException(
                            "Password must contain at least 6 characters."
                    );
                }

                trainer.setPassword(
                        passwordEncoder.encode(newPassword)
                );
            }

            trainer.setRole(User.Role.TRAINER);

            userRepository.save(trainer);

            return "redirect:/admin/trainers?success=trainer-updated";

        } catch (RuntimeException e) {

            model.addAttribute(
                    "error",
                    e.getMessage()
            );

            model.addAttribute(
                    "trainer",
                    trainer
            );

            addAdminDetails(model);

            return "admin/trainer-edit";
        }
    }


    // =========================================================
    // ENABLE TRAINER
    // =========================================================

    @PostMapping("/trainers/{id}/enable")
    public String enableTrainer(
            @PathVariable Long id) {

        User user = userService.getUser(id);

        if (user.getRole() != User.Role.TRAINER) {
            throw new RuntimeException(
                    "Selected user is not a trainer."
            );
        }

        userService.enableUser(id);

        return "redirect:/admin/trainers?success=trainer-enabled";
    }


    // =========================================================
    // DISABLE TRAINER
    // =========================================================

    @PostMapping("/trainers/{id}/disable")
    public String disableTrainer(
            @PathVariable Long id) {

        User user = userService.getUser(id);

        if (user.getRole() != User.Role.TRAINER) {
            throw new RuntimeException(
                    "Selected user is not a trainer."
            );
        }

        userService.disableUser(id);

        return "redirect:/admin/trainers?success=trainer-disabled";
    }


    // =========================================================
    // DELETE TRAINER
    // =========================================================

    @PostMapping("/trainers/{id}/delete")
    public String deleteTrainer(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes) {

        try {

            User trainer =
                    userRepository.findById(id).orElse(null);

            if (trainer == null) {

                redirectAttributes.addFlashAttribute(
                        "error",
                        "Trainer not found."
                );

                return "redirect:/admin/trainers";
            }

            if (trainer.getRole() != Role.TRAINER) {

                redirectAttributes.addFlashAttribute(
                        "error",
                        "This user is not a trainer."
                );

                return "redirect:/admin/trainers";
            }

            long assignedEmployees =
                    userRepository.countByTrainerAndRole(
                            trainer,
                            User.Role.EMPLOYEE
                    );

            if (assignedEmployees > 0) {

                redirectAttributes.addFlashAttribute(
                        "error",
                        "Cannot delete this trainer because "
                                + assignedEmployees
                                + " employee(s) are assigned to this trainer. "
                                + "Reassign or remove the employees first."
                );

                return "redirect:/admin/trainers";
            }

            userRepository.delete(trainer);

            redirectAttributes.addFlashAttribute(
                    "success",
                    "Trainer deleted successfully."
            );

        } catch (Exception e) {

            redirectAttributes.addFlashAttribute(
                    "error",
                    "Unable to delete trainer. "
                            + e.getMessage()
            );
        }

        return "redirect:/admin/trainers";
    }


    // =========================================================
    // TRAINER INVITATION
    // =========================================================

    @GetMapping("/trainers/invite")
    public String inviteTrainer() {
        return "admin/invite-trainer";
    }


    // =========================================================
    // INVITATIONS
    // =========================================================

    @GetMapping("/invitations")
    public String invitations(Model model) {

        LocalDateTime now = LocalDateTime.now();

        model.addAttribute(
                "invitations",
                trainerInvitationRepository.findAll()
        );

        model.addAttribute(
                "totalInvitations",
                trainerInvitationRepository.count()
        );

        model.addAttribute(
                "pendingInvitations",
                trainerInvitationRepository
                        .countByUsedFalseAndExpiresAtAfter(now)
        );

        model.addAttribute(
                "acceptedInvitations",
                trainerInvitationRepository.countByUsedTrue()
        );

        model.addAttribute(
                "expiredInvitations",
                trainerInvitationRepository
                        .countByUsedFalseAndExpiresAtBefore(now)
        );

        model.addAttribute("userName", "Administrator");
        model.addAttribute("userInitial", "A");

        return "admin/invitations/index";
    }


    // =========================================================
    // SKILL HEATMAP
    // =========================================================

    @GetMapping("/skill-heatmap")
    public String skillHeatmap(Model model) {

        List<User> employees =
                userRepository.findByRoleOrderByFullNameAsc(
                        User.Role.EMPLOYEE
                );

        List<Map<String, Object>> skillHeatmap =
                new ArrayList<>();

        int strongSkills = 0;
        int moderateSkills = 0;
        int criticalSkills = 0;
        int totalSkills = 0;

        for (User employee : employees) {

            Map<String, Object> item =
                    new HashMap<>();

            String fullName =
                    employee.getFullName();

            if (fullName == null ||
                    fullName.trim().isEmpty()) {

                fullName = "Employee";
            }

            fullName = fullName.trim();

            String initials =
                    buildInitials(fullName);

            item.put("name", fullName);

            item.put(
                    "department",
                    employee.getDepartment() != null
                            ? employee.getDepartment()
                            : "Not Assigned"
            );

            item.put("initials", initials);

            item.put("javaScore", null);
            item.put("javaLevel", "heat-none");

            item.put("springScore", null);
            item.put("springLevel", "heat-none");

            item.put("sqlScore", null);
            item.put("sqlLevel", "heat-none");

            item.put("pythonScore", null);
            item.put("pythonLevel", "heat-none");

            item.put("communicationScore", null);
            item.put("communicationLevel", "heat-none");

            item.put("problemSolvingScore", null);
            item.put("problemSolvingLevel", "heat-none");

            item.put("leadershipScore", null);
            item.put("leadershipLevel", "heat-none");

            List<AssessmentAssignment> completedAssignments =
                    assessmentAssignmentRepository
                            .findByEmployeeAndStatusOrderByAssignedAtDesc(
                                    employee,
                                    "COMPLETED"
                            );

            if (completedAssignments == null
                    || completedAssignments.isEmpty()) {

                item.put(
                        "assessmentStatus",
                        "Not Assessed"
                );

                skillHeatmap.add(item);

                continue;
            }

            AssessmentAssignment latestAssignment =
                    completedAssignments.get(0);

            try {

                SkillAnalysisService.SkillAnalysisResult analysis =
                        skillAnalysisService.analyzeAssignment(
                                latestAssignment
                        );

                item.put(
                        "assessmentStatus",
                        "Assessed"
                );

                item.put(
                        "overallScore",
                        analysis.percentage()
                );

                item.put(
                        "overallObtainedMarks",
                        analysis.obtainedMarks()
                );

                item.put(
                        "overallMaxMarks",
                        analysis.maxMarks()
                );

                item.put(
                        "overallCorrectAnswers",
                        analysis.correctAnswers()
                );

                item.put(
                        "overallTotalQuestions",
                        analysis.totalQuestions()
                );

                for (
                        SkillAnalysisService.SkillResult skill
                        : analysis.skills()
                ) {

                    if (skill == null) {
                        continue;
                    }

                    int score =
                            skill.percentage();

                    String competencyName =
                            skill.competencyName();

                    if (competencyName == null) {
                        continue;
                    }

                    String normalizedName =
                            competencyName
                                    .trim()
                                    .toLowerCase();

                    String heatClass =
                            getHeatClass(score);

                    totalSkills++;

                    if (score >= 80) {
                        strongSkills++;
                    } else if (score >= 60) {
                        moderateSkills++;
                    } else {
                        criticalSkills++;
                    }

                    if (containsAny(
                            normalizedName,
                            "java"
                    )) {

                        item.put("javaScore", score);
                        item.put("javaLevel", heatClass);
                    }

                    if (containsAny(
                            normalizedName,
                            "spring boot",
                            "springboot",
                            "spring"
                    )) {

                        item.put("springScore", score);
                        item.put("springLevel", heatClass);
                    }

                    if (containsAny(
                            normalizedName,
                            "sql",
                            "mysql",
                            "database",
                            "dbms"
                    )) {

                        item.put("sqlScore", score);
                        item.put("sqlLevel", heatClass);
                    }

                    if (containsAny(
                            normalizedName,
                            "python",
                            "django",
                            "flask"
                    )) {

                        item.put("pythonScore", score);
                        item.put("pythonLevel", heatClass);
                    }

                    if (containsAny(
                            normalizedName,
                            "communication",
                            "communic"
                    )) {

                        item.put(
                                "communicationScore",
                                score
                        );

                        item.put(
                                "communicationLevel",
                                heatClass
                        );
                    }

                    if (containsAny(
                            normalizedName,
                            "problem solving",
                            "problem-solving",
                            "problem solving skills",
                            "logical reasoning"
                    )) {

                        item.put(
                                "problemSolvingScore",
                                score
                        );

                        item.put(
                                "problemSolvingLevel",
                                heatClass
                        );
                    }

                    if (containsAny(
                            normalizedName,
                            "leadership",
                            "team leadership",
                            "management"
                    )) {

                        item.put(
                                "leadershipScore",
                                score
                        );

                        item.put(
                                "leadershipLevel",
                                heatClass
                        );
                    }
                }

            } catch (RuntimeException e) {

                System.err.println(
                        "Unable to build skill heatmap for employee "
                                + employee.getId()
                                + ": "
                                + e.getMessage()
                );

                item.put(
                        "assessmentStatus",
                        "Analysis Error"
                );
            }

            skillHeatmap.add(item);
        }

        model.addAttribute(
                "skillHeatmap",
                skillHeatmap
        );

        model.addAttribute(
                "totalSkills",
                totalSkills
        );

        model.addAttribute(
                "strongSkills",
                strongSkills
        );

        model.addAttribute(
                "moderateSkills",
                moderateSkills
        );

        model.addAttribute(
                "criticalSkills",
                criticalSkills
        );

        model.addAttribute(
                "userName",
                "Administrator"
        );

        model.addAttribute(
                "userInitial",
                "A"
        );

        return "admin/skill-heatmap";
    }


    // =========================================================
    // SKILL GAPS
    // =========================================================

    @GetMapping("/skill-gaps")
    public String skillGaps(Model model) {

        List<User> employees =
                userRepository.findByRoleOrderByFullNameAsc(
                        User.Role.EMPLOYEE
                );

        List<Map<String, Object>> skillGaps =
                new ArrayList<>();

        int totalSkillGaps = 0;
        int criticalGaps = 0;
        int moderateGaps = 0;
        int lowGaps = 0;


        // =====================================================
        // EMPLOYEE LOOP
        // =====================================================

        for (User employee : employees) {

            String employeeName =
                    employee.getFullName();

            if (employeeName == null ||
                    employeeName.trim().isEmpty()) {

                employeeName = "Employee";
            }

            employeeName =
                    employeeName.trim();

            String initials =
                    buildInitials(employeeName);


            // =================================================
            // FIND COMPLETED ASSESSMENT
            // =================================================

            List<AssessmentAssignment> completedAssignments =
                    assessmentAssignmentRepository
                            .findByEmployeeAndStatusOrderByAssignedAtDesc(
                                    employee,
                                    "COMPLETED"
                            );


            // =================================================
            // NOT ASSESSED
            // =================================================

            if (completedAssignments == null
                    || completedAssignments.isEmpty()) {

                Map<String, Object> gap =
                        createEmployeeGapBase(
                                employee,
                                employeeName,
                                initials
                        );

                gap.put(
                        "skillName",
                        "Not Assessed"
                );

                gap.put(
                        "description",
                        "Skill assessment has not been completed yet."
                );

                gap.put(
                        "currentLevel",
                        "Not Assessed"
                );

                gap.put(
                        "requiredLevel",
                        "Not Assessed"
                );

                gap.put(
                        "currentScore",
                        0
                );

                gap.put(
                        "requiredScore",
                        0
                );

                gap.put(
                        "gapPercentage",
                        0
                );

                gap.put(
                        "assessmentScore",
                        0
                );

                gap.put(
                        "severity",
                        "Not Assessed"
                );

                gap.put(
                        "progressClass",
                        "gray"
                );

                gap.put(
                        "badgeClass",
                        "gap-not-assessed"
                );

                gap.put(
                        "status",
                        "NOT_ASSESSED"
                );

                skillGaps.add(gap);

                continue;
            }


            // =================================================
            // LATEST COMPLETED ASSESSMENT
            // =================================================

            AssessmentAssignment latestAssignment =
                    completedAssignments.get(0);


            // =================================================
            // ANALYZE ASSESSMENT
            // =================================================

            try {

                SkillAnalysisService.SkillAnalysisResult analysis =
                        skillAnalysisService.analyzeAssignment(
                                latestAssignment
                        );


                /*
                 * SkillAnalysisService already identifies
                 * competencies below 60% as skill gaps.
                 */
                List<SkillAnalysisService.SkillResult> gaps =
                        analysis.skillGaps();


                // =================================================
                // NO SKILL GAP
                // =================================================

                if (gaps == null || gaps.isEmpty()) {

                    Map<String, Object> noGap =
                            createEmployeeGapBase(
                                    employee,
                                    employeeName,
                                    initials
                            );

                    noGap.put(
                            "skillName",
                            "No Skill Gap"
                    );

                    noGap.put(
                            "description",
                            "The employee has reached at least 60% in all assessed competencies."
                    );

                    noGap.put(
                            "currentLevel",
                            "ON TRACK"
                    );

                    noGap.put(
                            "requiredLevel",
                            "MET"
                    );

                    noGap.put(
                            "currentScore",
                            analysis.percentage()
                    );

                    noGap.put(
                            "requiredScore",
                            60
                    );

                    noGap.put(
                            "gapPercentage",
                            analysis.percentage()
                    );

                    noGap.put(
                            "assessmentScore",
                            analysis.percentage()
                    );

                    noGap.put(
                            "overallScore",
                            analysis.percentage()
                    );

                    noGap.put(
                            "severity",
                            "No Gap"
                    );

                    noGap.put(
                            "progressClass",
                            "green"
                    );

                    noGap.put(
                            "badgeClass",
                            "gap-low"
                    );

                    noGap.put(
                            "status",
                            "NO_GAP"
                    );

                    skillGaps.add(noGap);

                    continue;
                }


                // =================================================
                // REAL COMPETENCY GAPS
                // =================================================

                for (
                        SkillAnalysisService.SkillResult result
                        : gaps
                ) {

                    if (result == null) {
                        continue;
                    }


                    int percentage =
                            result.percentage();


                    String severity =
                            normalizeSeverity(
                                    result.gap()
                            );


                    Map<String, Object> gap =
                            createEmployeeGapBase(
                                    employee,
                                    employeeName,
                                    initials
                            );


                    // =================================================
                    // COMPETENCY
                    // =================================================

                    gap.put(
                            "competencyId",
                            result.competencyId()
                    );

                    gap.put(
                            "skillName",
                            safeText(
                                    result.competencyName(),
                                    "Unknown Competency"
                            )
                    );

                    gap.put(
                            "category",
                            safeText(
                                    result.category(),
                                    "General"
                            )
                    );


                    // =================================================
                    // LEVEL INFORMATION
                    // =================================================

                    String currentLevel =
                            safeText(
                                    result.currentLevel(),
                                    "BEGINNER"
                            );

                    String requiredLevel =
                            safeText(
                                    result.requiredLevel(),
                                    "INTERMEDIATE"
                            );


                    gap.put(
                            "currentLevel",
                            currentLevel
                    );

                    gap.put(
                            "requiredLevel",
                            requiredLevel
                    );


                    // =================================================
                    // NUMERIC SCORE INFORMATION
                    // =================================================

                    /*
                     * IMPORTANT:
                     *
                     * currentLevel = BEGINNER / INTERMEDIATE /
                     *                ADVANCED
                     *
                     * currentScore = ACTUAL percentage
                     *
                     * Example:
                     *
                     * Django
                     * obtainedMarks = 4
                     * maxMarks      = 10
                     *
                     * currentScore  = 40
                     *
                     * Therefore the UI must use currentScore
                     * for the numeric progress bar.
                     */

                    gap.put(
                            "currentScore",
                            percentage
                    );

                    gap.put(
                            "requiredScore",
                            levelToPercentage(
                                    requiredLevel
                            )
                    );

                    gap.put(
                            "assessmentScore",
                            percentage
                    );

                    /*
                     * Kept for compatibility with the
                     * existing skill-gaps.html.
                     */
                    gap.put(
                            "gapPercentage",
                            percentage
                    );

                    gap.put(
                            "skillPercentage",
                            percentage
                    );


                    // =================================================
                    // MARKS
                    // =================================================

                    gap.put(
                            "obtainedMarks",
                            result.obtainedMarks()
                    );

                    gap.put(
                            "maxMarks",
                            result.maxMarks()
                    );

                    gap.put(
                            "correctAnswers",
                            result.correctAnswers()
                    );

                    gap.put(
                            "totalQuestions",
                            result.totalQuestions()
                    );


                    // =================================================
                    // PRIORITY
                    // =================================================

                    gap.put(
                            "severity",
                            severity
                    );

                    gap.put(
                            "priority",
                            severity
                    );

                    gap.put(
                            "gap",
                            severity
                    );


                    // =================================================
                    // PROGRESS / BADGE CLASSES
                    // =================================================

                    gap.put(
                            "progressClass",
                            getGapProgressClass(
                                    severity
                            )
                    );

                    gap.put(
                            "badgeClass",
                            getGapBadgeClass(
                                    severity
                            )
                    );


                    // =================================================
                    // DESCRIPTION
                    // =================================================

                    gap.put(
                            "description",
                            buildSkillGapDescription(
                                    result
                            )
                    );


                    // =================================================
                    // COMPLETE ASSESSMENT INFORMATION
                    // =================================================

                    gap.put(
                            "overallScore",
                            analysis.percentage()
                    );

                    gap.put(
                            "assessmentObtainedMarks",
                            analysis.obtainedMarks()
                    );

                    gap.put(
                            "assessmentMaxMarks",
                            analysis.maxMarks()
                    );

                    gap.put(
                            "assessmentCorrectAnswers",
                            analysis.correctAnswers()
                    );

                    gap.put(
                            "assessmentTotalQuestions",
                            analysis.totalQuestions()
                    );

                    gap.put(
                            "assessmentId",
                            latestAssignment.getId()
                    );

                    gap.put(
                            "status",
                            "GAP"
                    );


                    // =================================================
                    // ADD TO LIST
                    // =================================================

                    skillGaps.add(gap);


                    // =================================================
                    // COUNTERS
                    // =================================================

                    totalSkillGaps++;

                    switch (severity) {

                        case "HIGH" ->
                                criticalGaps++;

                        case "MEDIUM" ->
                                moderateGaps++;

                        case "LOW" ->
                                lowGaps++;
                    }
                }


            } catch (RuntimeException e) {

                System.err.println(
                        "Unable to analyze skill gaps for employee "
                                + employee.getId()
                                + ": "
                                + e.getMessage()
                );


                Map<String, Object> errorGap =
                        createEmployeeGapBase(
                                employee,
                                employeeName,
                                initials
                        );

                errorGap.put(
                        "skillName",
                        "Analysis Error"
                );

                errorGap.put(
                        "description",
                        "The completed assessment could not be analyzed."
                );

                errorGap.put(
                        "currentLevel",
                        "Unavailable"
                );

                errorGap.put(
                        "requiredLevel",
                        "Unavailable"
                );

                errorGap.put(
                        "currentScore",
                        0
                );

                errorGap.put(
                        "requiredScore",
                        0
                );

                errorGap.put(
                        "gapPercentage",
                        0
                );

                errorGap.put(
                        "assessmentScore",
                        0
                );

                errorGap.put(
                        "severity",
                        "Not Available"
                );

                errorGap.put(
                        "progressClass",
                        "gray"
                );

                errorGap.put(
                        "badgeClass",
                        "gap-not-assessed"
                );

                errorGap.put(
                        "status",
                        "ERROR"
                );

                skillGaps.add(errorGap);
            }
        }


        // =========================================================
        // MODEL
        // =========================================================

        model.addAttribute(
                "skillGaps",
                skillGaps
        );

        model.addAttribute(
                "totalSkillGaps",
                totalSkillGaps
        );

        model.addAttribute(
                "criticalGaps",
                criticalGaps
        );

        model.addAttribute(
                "moderateGaps",
                moderateGaps
        );

        model.addAttribute(
                "lowGaps",
                lowGaps
        );

        model.addAttribute(
                "userName",
                "Administrator"
        );

        model.addAttribute(
                "userInitial",
                "A"
        );

        return "admin/skill-gaps";
    }


    // =========================================================
    // SETTINGS PAGE
    // =========================================================

    @GetMapping("/settings")
    public String settings(
            Authentication authentication,
            Model model) {

        User admin =
                userService.getUserByEmail(
                        authentication.getName()
                );

        OrganizationSettings settings =
                organizationSettingsService.getSettings();

        model.addAttribute(
                "settings",
                settings
        );

        model.addAttribute(
                "admin",
                admin
        );

        String adminName =
                admin.getFullName();

        if (adminName == null ||
                adminName.trim().isEmpty()) {

            adminName = "Administrator";
        }

        model.addAttribute(
                "adminName",
                adminName
        );

        model.addAttribute(
                "adminEmail",
                admin.getEmail()
        );

        model.addAttribute(
                "userName",
                adminName
        );

        model.addAttribute(
                "userInitial",
                adminName
                        .substring(0, 1)
                        .toUpperCase()
        );

        return "admin/settings";
    }


    // =========================================================
    // SAVE ORGANIZATION SETTINGS
    // =========================================================

    @PostMapping("/settings/organization")
    public String saveOrganizationSettings(
            @RequestParam String organizationName,

            @RequestParam(required = false)
            String organizationType,

            @RequestParam(required = false)
            String contactEmail,

            @RequestParam(required = false)
            String contactPhone,

            @RequestParam(required = false)
            String address) {

        organizationSettingsService.saveOrganizationSettings(
                organizationName,
                organizationType,
                contactEmail,
                contactPhone,
                address
        );

        return "redirect:/admin/settings?success=organization";
    }


    // =========================================================
    // UPDATE ADMIN PROFILE
    // =========================================================

    @PostMapping("/settings/profile")
    public String updateAdminProfile(
            Authentication authentication,

            @RequestParam String fullName,

            @RequestParam String email,

            @RequestParam(required = false)
            String phoneNumber) {

        User admin =
                userService.getUserByEmail(
                        authentication.getName()
                );

        userService.updateAdminProfile(
                admin.getId(),
                fullName,
                email,
                phoneNumber
        );

        return "redirect:/admin/settings?success=profile";
    }


    // =========================================================
    // CHANGE ADMIN PASSWORD
    // =========================================================

    @PostMapping("/settings/password")
    public String changeAdminPassword(
            Authentication authentication,

            @RequestParam String currentPassword,

            @RequestParam String newPassword,

            @RequestParam String confirmPassword) {

        User admin =
                userService.getUserByEmail(
                        authentication.getName()
                );

        userService.changeAdminPassword(
                admin.getId(),
                currentPassword,
                newPassword,
                confirmPassword
        );

        return "redirect:/admin/settings?success=password";
    }


    // =========================================================
    // SAVE NOTIFICATION SETTINGS
    // =========================================================

    @PostMapping("/settings/notifications")
    public String saveNotificationSettings(

            @RequestParam(defaultValue = "false")
            boolean notifyEmployeeInvitation,

            @RequestParam(defaultValue = "false")
            boolean notifyTrainingCompletion,

            @RequestParam(defaultValue = "false")
            boolean notifyAssessmentCompletion,

            @RequestParam(defaultValue = "false")
            boolean notifySkillGap,

            @RequestParam(defaultValue = "false")
            boolean notifyWeeklyReports) {

        organizationSettingsService.saveNotificationSettings(
                notifyEmployeeInvitation,
                notifyTrainingCompletion,
                notifyAssessmentCompletion,
                notifySkillGap,
                notifyWeeklyReports
        );

        return "redirect:/admin/settings?success=notifications";
    }


    // =========================================================
    // SAVE SYSTEM SETTINGS
    // =========================================================

    @PostMapping("/settings/system")
    public String saveSystemSettings(

            @RequestParam(required = false)
            String defaultLanguage,

            @RequestParam(required = false)
            String timeZone,

            @RequestParam(required = false)
            String dateFormat,

            @RequestParam(required = false)
            String dashboardRefresh) {

        organizationSettingsService.saveSystemSettings(
                defaultLanguage,
                timeZone,
                dateFormat,
                dashboardRefresh
        );

        return "redirect:/admin/settings?success=system";
    }


    // =========================================================
    // DISABLE ORGANIZATION
    // =========================================================

    @PostMapping("/settings/disable-organization")
    public String disableOrganization() {

        organizationSettingsService.disableOrganization();

        return "redirect:/admin/settings?success=disabled";
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


    // =========================================================
    // BUILD EMPLOYEE INITIALS
    // =========================================================

    private String buildInitials(
            String fullName
    ) {

        if (fullName == null ||
                fullName.trim().isEmpty()) {

            return "E";
        }

        String[] nameParts =
                fullName.trim().split("\\s+");

        if (nameParts.length >= 2) {

            return String.valueOf(
                    Character.toUpperCase(
                            nameParts[0].charAt(0)
                    )
            )
                    +
                    String.valueOf(
                            Character.toUpperCase(
                                    nameParts[
                                            nameParts.length - 1
                                    ].charAt(0)
                            )
                    );
        }

        return String.valueOf(
                Character.toUpperCase(
                        fullName.trim().charAt(0)
                )
        );
    }


    // =========================================================
    // CREATE COMMON EMPLOYEE GAP DATA
    // =========================================================

    private Map<String, Object> createEmployeeGapBase(
            User employee,
            String employeeName,
            String initials
    ) {

        Map<String, Object> gap =
                new HashMap<>();

        gap.put(
                "id",
                employee.getId()
        );

        gap.put(
                "employeeId",
                employee.getId()
        );

        gap.put(
                "employeeName",
                employeeName
        );

        gap.put(
                "initials",
                initials
        );

        gap.put(
                "department",
                employee.getDepartment() != null
                        ? employee.getDepartment()
                        : "Not Assigned"
        );

        return gap;
    }


    // =========================================================
    // NORMALIZE SEVERITY
    // =========================================================

    private String normalizeSeverity(
            String severity
    ) {

        if (severity == null ||
                severity.trim().isEmpty()) {

            return "LOW";
        }

        String normalized =
                severity.trim().toUpperCase();

        return switch (normalized) {

            case "HIGH" ->
                    "HIGH";

            case "MEDIUM" ->
                    "MEDIUM";

            case "LOW" ->
                    "LOW";

            default ->
                    "LOW";
        };
    }


    // =========================================================
    // GAP PROGRESS CLASS
    // =========================================================

    private String getGapProgressClass(
            String severity
    ) {

        return switch (normalizeSeverity(severity)) {

            case "HIGH" ->
                    "red";

            case "MEDIUM" ->
                    "yellow";

            default ->
                    "green";
        };
    }


    // =========================================================
    // GAP BADGE CLASS
    // =========================================================

    private String getGapBadgeClass(
            String severity
    ) {

        return switch (normalizeSeverity(severity)) {

            case "HIGH" ->
                    "gap-high";

            case "MEDIUM" ->
                    "gap-medium";

            default ->
                    "gap-low";
        };
    }


    // =========================================================
    // CONVERT REQUIRED LEVEL TO NUMERIC TARGET
    // =========================================================

    private int levelToPercentage(
            String level
    ) {

        if (level == null ||
                level.trim().isEmpty()) {

            return 60;
        }

        return switch (
                level.trim().toUpperCase()
        ) {

            case "ADVANCED",
                 "EXPERT" ->
                    80;

            case "INTERMEDIATE",
                 "MEDIUM",
                 "DEVELOPING" ->
                    60;

            case "BEGINNER",
                 "BASIC" ->
                    20;

            default ->
                    60;
        };
    }


    // =========================================================
    // HEATMAP CLASS
    // =========================================================

    private String getHeatClass(
            int percentage
    ) {

        if (percentage >= 80) {
            return "heat-strong";
        }

        if (percentage >= 60) {
            return "heat-moderate";
        }

        if (percentage >= 40) {
            return "heat-critical";
        }

        return "heat-critical";
    }


    // =========================================================
    // CHECK COMPETENCY NAME
    // =========================================================

    private boolean containsAny(
            String value,
            String... keywords
    ) {

        if (value == null ||
                value.isBlank()) {

            return false;
        }

        for (String keyword : keywords) {

            if (keyword == null ||
                    keyword.isBlank()) {

                continue;
            }

            if (value.contains(
                    keyword.toLowerCase()
            )) {

                return true;
            }
        }

        return false;
    }


    // =========================================================
    // SAFE TEXT
    // =========================================================

    private String safeText(
            String value,
            String fallback
    ) {

        if (value == null ||
                value.trim().isEmpty()) {

            return fallback;
        }

        return value.trim();
    }


    // =========================================================
    // SKILL GAP DESCRIPTION
    // =========================================================

    private String buildSkillGapDescription(
            SkillAnalysisService.SkillResult result
    ) {

        String skillName =
                safeText(
                        result.competencyName(),
                        "This competency"
                );

        String currentLevel =
                safeText(
                        result.currentLevel(),
                        "BEGINNER"
                );

        String requiredLevel =
                safeText(
                        result.requiredLevel(),
                        "INTERMEDIATE"
                );

        int percentage =
                result.percentage();

        return skillName
                + " is currently at "
                + currentLevel
                + " with an assessment score of "
                + percentage
                + "%. Required level: "
                + requiredLevel
                + ".";
    }


    // =========================================================
    // CLEAN OPTIONAL VALUE
    // =========================================================

    private String cleanOptionalValue(
            String value) {

        if (value == null ||
                value.trim().isEmpty()) {

            return null;
        }

        return value.trim();
    }
    // =========================================================
// VIEW SKILL GAP DETAILS
// =========================================================

@GetMapping("/skill-gaps/{id}")
public String viewSkillGap(
        @PathVariable Long id,
        Model model) {

    // ---------------------------------------------------------
    // Find employee
    // ---------------------------------------------------------

    User employee =
            userRepository.findById(id).orElse(null);

    if (employee == null ||
            employee.getRole() != User.Role.EMPLOYEE) {

        return "redirect:/admin/skill-gaps";
    }


    // ---------------------------------------------------------
    // Find latest completed assessment
    // ---------------------------------------------------------

    List<AssessmentAssignment> completedAssignments =
            assessmentAssignmentRepository
                    .findByEmployeeAndStatusOrderByAssignedAtDesc(
                            employee,
                            "COMPLETED"
                    );


    // ---------------------------------------------------------
    // Employee has no completed assessment
    // ---------------------------------------------------------

    if (completedAssignments == null ||
            completedAssignments.isEmpty()) {

        model.addAttribute(
                "employee",
                employee
        );

        model.addAttribute(
                "hasAssessment",
                false
        );

        model.addAttribute(
                "skillGaps",
                List.of()
        );

        model.addAttribute(
                "skillAnalysis",
                null
        );

        addAdminDetails(model);

        return "admin/skill-gap-details";
    }


    // ---------------------------------------------------------
    // Latest completed assessment
    // ---------------------------------------------------------

    AssessmentAssignment latestAssignment =
            completedAssignments.get(0);


    // ---------------------------------------------------------
    // Analyze assessment
    // ---------------------------------------------------------

    try {

        SkillAnalysisService.SkillAnalysisResult analysis =
                skillAnalysisService.analyzeAssignment(
                        latestAssignment
                );


        // -----------------------------------------------------
        // Add employee
        // -----------------------------------------------------

        model.addAttribute(
                "employee",
                employee
        );


        // -----------------------------------------------------
        // Assessment
        // -----------------------------------------------------

        model.addAttribute(
                "assessment",
                latestAssignment.getAssessment()
        );

        model.addAttribute(
                "assignment",
                latestAssignment
        );


        // -----------------------------------------------------
        // Complete analysis
        // -----------------------------------------------------

        model.addAttribute(
                "skillAnalysis",
                analysis
        );


        // -----------------------------------------------------
        // Competency results
        // -----------------------------------------------------

        model.addAttribute(
                "skills",
                analysis.skills()
        );


        // -----------------------------------------------------
        // Actual skill gaps
        // -----------------------------------------------------

        model.addAttribute(
                "skillGaps",
                analysis.skillGaps()
        );


        // -----------------------------------------------------
        // Overall assessment statistics
        // -----------------------------------------------------

        model.addAttribute(
                "overallScore",
                analysis.percentage()
        );

        model.addAttribute(
                "obtainedMarks",
                analysis.obtainedMarks()
        );

        model.addAttribute(
                "maxMarks",
                analysis.maxMarks()
        );

        model.addAttribute(
                "correctAnswers",
                analysis.correctAnswers()
        );

        model.addAttribute(
                "totalQuestions",
                analysis.totalQuestions()
        );


        model.addAttribute(
                "hasAssessment",
                true
        );


        // -----------------------------------------------------
        // Employee initials
        // -----------------------------------------------------

        String employeeName =
                employee.getFullName();

        model.addAttribute(
                "employeeInitials",
                buildInitials(employeeName)
        );


        addAdminDetails(model);


        return "admin/skill-gap-details";


    } catch (RuntimeException e) {

        System.err.println(
                "Unable to load skill gap details for employee "
                        + employee.getId()
                        + ": "
                        + e.getMessage()
        );

        model.addAttribute(
                "employee",
                employee
        );

        model.addAttribute(
                "hasAssessment",
                false
        );

        model.addAttribute(
                "skillGaps",
                List.of()
        );

        model.addAttribute(
                "skillAnalysis",
                null
        );

        model.addAttribute(
                "errorMessage",
                "The assessment could not be analyzed."
        );

        addAdminDetails(model);

        return "admin/skill-gap-details";
    }
}
}