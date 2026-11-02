
package com.capacitix.service;

import com.capacitix.entity.User;
import com.capacitix.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;


    // =========================================================
    // ADMIN CREATES EMPLOYEE
    // =========================================================

    public User createEmployee(User user) {

        validateEmail(user.getEmail());
        validateEmployeeId(user.getEmployeeId());

        // Never trust role coming from form.
        user.setRole(User.Role.EMPLOYEE);

        // Employee can login.
        user.setEnabled(true);

        // Generate temporary password.
        String temporaryPassword =
                UUID.randomUUID().toString();

        user.setPassword(
                passwordEncoder.encode(temporaryPassword)
        );

        // Trainer assignment is handled separately.
        user.setTrainer(null);

        return userRepository.save(user);
    }


    // =========================================================
    // ADMIN CREATES TRAINER
    // =========================================================

    public User createTrainer(User user) {

        validateEmail(user.getEmail());
        validateTrainerId(user.getTrainerId());
        validatePassword(user.getPassword());

        // Never trust role coming from form.
        user.setRole(User.Role.TRAINER);

        user.setEnabled(true);

        user.setPassword(
                passwordEncoder.encode(user.getPassword())
        );

        // Trainer does not have another trainer.
        user.setTrainer(null);

        return userRepository.save(user);
    }


    // =========================================================
    // ADMIN CREATES ORGANIZATION
    // =========================================================

    public User createOrganization(User user) {

        validateEmail(user.getEmail());
        validatePassword(user.getPassword());

        user.setRole(User.Role.ORGANIZATION);
        user.setEnabled(true);

        user.setPassword(
                passwordEncoder.encode(user.getPassword())
        );

        user.setTrainer(null);

        return userRepository.save(user);
    }


    // =========================================================
    // ADMIN CREATES ADMIN
    // =========================================================

    public User createAdmin(User user) {

        validateEmail(user.getEmail());
        validatePassword(user.getPassword());

        user.setRole(User.Role.ADMIN);
        user.setEnabled(true);

        user.setPassword(
                passwordEncoder.encode(user.getPassword())
        );

        user.setTrainer(null);

        return userRepository.save(user);
    }


    // =========================================================
    // ASSIGN TRAINER TO EMPLOYEE
    // =========================================================

    public User assignTrainer(
            Long employeeId,
            Long trainerId) {

        User employee =
                userRepository.findById(employeeId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Employee not found."
                                )
                        );

        if (employee.getRole() != User.Role.EMPLOYEE) {

            throw new IllegalArgumentException(
                    "Selected user is not an employee."
            );
        }

        User trainer =
                userRepository.findById(trainerId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Trainer not found."
                                )
                        );

        if (trainer.getRole() != User.Role.TRAINER) {

            throw new IllegalArgumentException(
                    "Selected user is not a trainer."
            );
        }

        if (!trainer.isEnabled()) {

            throw new IllegalArgumentException(
                    "Selected trainer is disabled."
            );
        }

        employee.setTrainer(trainer);

        return userRepository.save(employee);
    }


    // =========================================================
    // REMOVE TRAINER FROM EMPLOYEE
    // =========================================================

    public User removeTrainer(Long employeeId) {

        User employee =
                userRepository.findById(employeeId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Employee not found."
                                )
                        );

        if (employee.getRole() != User.Role.EMPLOYEE) {

            throw new IllegalArgumentException(
                    "Selected user is not an employee."
            );
        }

        employee.setTrainer(null);

        return userRepository.save(employee);
    }


    // =========================================================
    // GET EMPLOYEES ASSIGNED TO TRAINER
    // =========================================================

    /*
     * Returns all employees assigned to the trainer.
     *
     * Used by:
     *
     * - Trainer dashboard
     * - Trainer employee list
     * - Video assignment
     */

    @Transactional(readOnly = true)
    public List<User> getTrainerEmployees(
            Long trainerId) {

        User trainer =
                getTrainer(trainerId);

        return userRepository
                .findByTrainerAndRoleOrderByFullNameAsc(
                        trainer,
                        User.Role.EMPLOYEE
                );
    }


    // =========================================================
    // GET ENABLED EMPLOYEES ASSIGNED TO TRAINER
    // =========================================================

    /*
     * IMPORTANT FOR VIDEO ASSIGNMENT
     *
     * Only enabled EMPLOYEE accounts assigned to the
     * logged-in trainer are returned.
     *
     * This list should be used in the Trainer's
     * "Assign Video" employee dropdown.
     *
     * Example:
     *
     * Trainer: Omkar Pawar
     *
     * Employees:
     *
     *   Prashant Ambule       ENABLED  -> shown
     *   Employee 2            ENABLED  -> shown
     *   Employee 3            DISABLED -> hidden
     *
     * Employees belonging to another trainer are also hidden.
     */

    @Transactional(readOnly = true)
    public List<User> getEnabledTrainerEmployees(
            Long trainerId) {

        User trainer =
                getTrainer(trainerId);

        return userRepository
                .findByTrainerAndRoleAndEnabledTrueOrderByFullNameAsc(
                        trainer,
                        User.Role.EMPLOYEE
                );
    }


    // =========================================================
    // VERIFY EMPLOYEE BELONGS TO TRAINER
    // =========================================================

    /*
     * SECURITY CHECK FOR VIDEO ASSIGNMENT
     *
     * This method verifies that:
     *
     * 1. Employee exists
     * 2. Employee belongs to the given trainer
     * 3. Employee has EMPLOYEE role
     * 4. Employee account is enabled
     *
     * This prevents a trainer from manually changing an
     * employee ID and assigning a video to someone else's employee.
     */

    @Transactional(readOnly = true)
    public User getTrainerEmployee(
            Long trainerId,
            Long employeeId) {

        User trainer =
                getTrainer(trainerId);

        return userRepository
                .findByIdAndTrainerAndRole(
                        employeeId,
                        trainer,
                        User.Role.EMPLOYEE
                )
                .filter(User::isEnabled)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Employee is not assigned to this trainer."
                        )
                );
    }


    // =========================================================
    // GET EMPLOYEE'S TRAINER
    // =========================================================

    @Transactional(readOnly = true)
    public User getEmployeeTrainer(
            Long employeeId) {

        User employee =
                userRepository.findById(employeeId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Employee not found."
                                )
                        );

        if (employee.getRole() != User.Role.EMPLOYEE) {

            throw new IllegalArgumentException(
                    "Selected user is not an employee."
            );
        }

        return employee.getTrainer();
    }


    // =========================================================
    // ENABLE USER
    // =========================================================

    public User enableUser(Long userId) {

        User user =
                getUser(userId);

        user.setEnabled(true);

        return userRepository.save(user);
    }


    // =========================================================
    // DISABLE USER
    // =========================================================

    public User disableUser(Long userId) {

        User user =
                getUser(userId);

        user.setEnabled(false);

        return userRepository.save(user);
    }


    // =========================================================
    // FIND USER BY ID
    // =========================================================

    @Transactional(readOnly = true)
    public User getUser(Long userId) {

        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "User not found."
                        )
                );
    }


    // =========================================================
    // FIND USER BY EMAIL
    // =========================================================

    @Transactional(readOnly = true)
    public User getUserByEmail(String email) {

        if (email == null ||
                email.trim().isEmpty()) {

            throw new RuntimeException(
                    "Email address is required."
            );
        }

        String cleanedEmail =
                email.trim().toLowerCase();

        return userRepository.findByEmail(cleanedEmail)
                .orElseThrow(() ->
                        new RuntimeException(
                                "User not found."
                        )
                );
    }


    // =========================================================
    // GET TRAINER BY DATABASE ID
    // =========================================================

    @Transactional(readOnly = true)
    public User getTrainer(Long trainerId) {

        User trainer =
                userRepository.findById(trainerId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Trainer not found."
                                )
                        );

        if (trainer.getRole() != User.Role.TRAINER) {

            throw new RuntimeException(
                    "The selected user is not a trainer."
            );
        }

        return trainer;
    }


    // =========================================================
    // UPDATE TRAINER
    // =========================================================

    public User updateTrainer(
            Long trainerId,
            String fullName,
            String email,
            String phoneNumber,
            String department,
            String designation,
            String trainerIdValue,
            String expertise,
            String password) {

        User trainer =
                getTrainer(trainerId);

        if (fullName == null ||
                fullName.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Full name is required."
            );
        }

        if (email == null ||
                email.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Email address is required."
            );
        }

        String cleanedEmail =
                email.trim().toLowerCase();

        userRepository.findByEmail(cleanedEmail)
                .ifPresent(existingUser -> {

                    if (!existingUser.getId()
                            .equals(trainer.getId())) {

                        throw new IllegalArgumentException(
                                "This email address is already in use."
                        );
                    }
                });

        if (trainerIdValue == null ||
                trainerIdValue.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Trainer ID is required."
            );
        }

        String cleanedTrainerId =
                trainerIdValue.trim();

        String existingTrainerId =
                trainer.getTrainerId();

        if (existingTrainerId == null ||
                !existingTrainerId.equals(
                        cleanedTrainerId)) {

            if (userRepository.existsByTrainerId(
                    cleanedTrainerId)) {

                throw new IllegalArgumentException(
                        "Trainer ID is already registered."
                );
            }
        }

        trainer.setFullName(
                fullName.trim()
        );

        trainer.setEmail(
                cleanedEmail
        );

        trainer.setPhoneNumber(
                cleanOptionalValue(phoneNumber)
        );

        trainer.setDepartment(
                cleanOptionalValue(department)
        );

        trainer.setDesignation(
                cleanOptionalValue(designation)
        );

        trainer.setTrainerId(
                cleanedTrainerId
        );

        trainer.setExpertise(
                cleanOptionalValue(expertise)
        );

        // Never change trainer role.
        trainer.setRole(
                User.Role.TRAINER
        );

        if (password != null &&
                !password.trim().isEmpty()) {

            validatePassword(password);

            trainer.setPassword(
                    passwordEncoder.encode(
                            password.trim()
                    )
            );
        }

        return userRepository.save(trainer);
    }


    // =========================================================
    // DISABLE TRAINER
    // =========================================================

    public User disableTrainer(Long trainerId) {

        User trainer =
                getTrainer(trainerId);

        trainer.setEnabled(false);

        return userRepository.save(trainer);
    }


    // =========================================================
    // ENABLE TRAINER
    // =========================================================

    public User enableTrainer(Long trainerId) {

        User trainer =
                getTrainer(trainerId);

        trainer.setEnabled(true);

        return userRepository.save(trainer);
    }


    // =========================================================
    // DELETE TRAINER
    // =========================================================

    public void deleteTrainer(Long trainerId) {

        User trainer =
                getTrainer(trainerId);

        long assignedEmployees =
                userRepository.countByTrainerAndRole(
                        trainer,
                        User.Role.EMPLOYEE
                );

        if (assignedEmployees > 0) {

            throw new IllegalStateException(
                    "Cannot delete trainer because " +
                    assignedEmployees +
                    " employee(s) are assigned to this trainer. " +
                    "Remove the assignments first."
            );
        }

        userRepository.delete(trainer);
    }


    // =========================================================
    // UPDATE PASSWORD
    // =========================================================

    public User updatePassword(
            String email,
            String newPassword) {

        if (email == null ||
                email.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Email address is required."
            );
        }

        if (newPassword == null ||
                newPassword.isBlank()) {

            throw new IllegalArgumentException(
                    "New password is required."
            );
        }

        if (newPassword.length() < 8) {

            throw new IllegalArgumentException(
                    "Password must contain at least 8 characters."
            );
        }

        String cleanedEmail =
                email.trim().toLowerCase();

        User user =
                userRepository.findByEmail(cleanedEmail)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "User not found."
                                )
                        );

        user.setPassword(
                passwordEncoder.encode(newPassword)
        );

        return userRepository.save(user);
    }


    // =========================================================
    // COMMON EMAIL VALIDATION
    // =========================================================

    private void validateEmail(String email) {

        if (email == null ||
                email.trim().isEmpty()) {

            throw new RuntimeException(
                    "Email address is required."
            );
        }

        String cleanedEmail =
                email.trim().toLowerCase();

        if (userRepository.existsByEmail(cleanedEmail)) {

            throw new RuntimeException(
                    "Email address is already registered."
            );
        }
    }


    // =========================================================
    // EMPLOYEE ID VALIDATION
    // =========================================================

    private void validateEmployeeId(
            String employeeId) {

        if (employeeId == null ||
                employeeId.trim().isEmpty()) {

            throw new RuntimeException(
                    "Employee ID is required."
            );
        }

        if (userRepository.existsByEmployeeId(
                employeeId.trim())) {

            throw new RuntimeException(
                    "Employee ID is already registered."
            );
        }
    }


    // =========================================================
    // TRAINER ID VALIDATION
    // =========================================================

    private void validateTrainerId(
            String trainerId) {

        if (trainerId == null ||
                trainerId.trim().isEmpty()) {

            throw new RuntimeException(
                    "Trainer ID is required."
            );
        }

        if (userRepository.existsByTrainerId(
                trainerId.trim())) {

            throw new RuntimeException(
                    "Trainer ID is already registered."
            );
        }
    }


    // =========================================================
    // PASSWORD VALIDATION
    // =========================================================

    private void validatePassword(
            String password) {

        if (password == null ||
                password.trim().isEmpty()) {

            throw new RuntimeException(
                    "Password is required."
            );
        }

        if (password.length() < 6) {

            throw new RuntimeException(
                    "Password must contain at least 6 characters."
            );
        }
    }


    // =========================================================
    // UPDATE ADMIN PROFILE
    // =========================================================

    @Transactional
    public User updateAdminProfile(
            Long userId,
            String fullName,
            String email,
            String phoneNumber) {

        User admin =
                userRepository.findById(userId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Admin user not found."
                                )
                        );

        if (admin.getRole() != User.Role.ADMIN) {

            throw new IllegalArgumentException(
                    "Only an admin account can update this profile."
            );
        }

        if (fullName == null ||
                fullName.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Full name is required."
            );
        }

        if (email == null ||
                email.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Email is required."
            );
        }

        String cleanedEmail =
                email.trim().toLowerCase();

        userRepository.findByEmail(cleanedEmail)
                .ifPresent(existingUser -> {

                    if (!existingUser.getId()
                            .equals(admin.getId())) {

                        throw new IllegalArgumentException(
                                "This email address is already in use."
                        );
                    }
                });

        admin.setFullName(
                fullName.trim()
        );

        admin.setEmail(
                cleanedEmail
        );

        admin.setPhoneNumber(
                cleanOptionalValue(phoneNumber)
        );

        return userRepository.save(admin);
    }


    // =========================================================
    // CHANGE ADMIN PASSWORD
    // =========================================================

    @Transactional
    public void changeAdminPassword(
            Long userId,
            String currentPassword,
            String newPassword,
            String confirmPassword) {

        User admin =
                userRepository.findById(userId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Admin user not found."
                                )
                        );

        if (admin.getRole() != User.Role.ADMIN) {

            throw new IllegalArgumentException(
                    "Only an admin account can change the password."
            );
        }

        if (currentPassword == null ||
                currentPassword.isBlank()) {

            throw new IllegalArgumentException(
                    "Current password is required."
            );
        }

        if (newPassword == null ||
                newPassword.isBlank()) {

            throw new IllegalArgumentException(
                    "New password is required."
            );
        }

        if (confirmPassword == null ||
                confirmPassword.isBlank()) {

            throw new IllegalArgumentException(
                    "Please confirm the new password."
            );
        }

        if (!passwordEncoder.matches(
                currentPassword,
                admin.getPassword())) {

            throw new IllegalArgumentException(
                    "Current password is incorrect."
            );
        }

        if (newPassword.length() < 6) {

            throw new IllegalArgumentException(
                    "New password must contain at least 6 characters."
            );
        }

        if (!newPassword.equals(confirmPassword)) {

            throw new IllegalArgumentException(
                    "New password and confirm password do not match."
            );
        }

        admin.setPassword(
                passwordEncoder.encode(newPassword)
        );

        userRepository.save(admin);
    }


    // =========================================================
    // OPTIONAL VALUE CLEANER
    // =========================================================

    private String cleanOptionalValue(
            String value) {

        if (value == null ||
                value.trim().isEmpty()) {

            return null;
        }

        return value.trim();
    }
}