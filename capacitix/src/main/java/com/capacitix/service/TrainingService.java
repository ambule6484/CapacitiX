package com.capacitix.service;

import com.capacitix.entity.Training;
import com.capacitix.entity.User;
import com.capacitix.repository.TrainingRepository;
import com.capacitix.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TrainingService {

    private final TrainingRepository trainingRepository;
    private final UserRepository userRepository;

    // =========================================================
    // GET ALL TRAININGS
    // =========================================================

    @Transactional(readOnly = true)
    public List<Training> getAllTrainings() {
        return trainingRepository.findAllByOrderByCreatedAtDesc();
    }

    // =========================================================
    // GET TRAINING BY ID
    // =========================================================

    @Transactional(readOnly = true)
    public Training getTraining(Long id) {

        return trainingRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Training not found with ID: " + id
                        )
                );
    }

    // =========================================================
    // GET ALL TRAINERS
    // =========================================================

    @Transactional(readOnly = true)
    public List<User> getAllTrainers() {

        return userRepository.findByRoleOrderByFullNameAsc(
                User.Role.TRAINER
        );
    }

    // =========================================================
    // CREATE TRAINING
    // =========================================================

    @Transactional
    public Training createTraining(
            String name,
            String description,
            String category,
            Long trainerId,
            String status,
            Integer assignedEmployees,
            Integer progress
    ) {

        // -----------------------------------------------------
        // VALIDATE NAME
        // -----------------------------------------------------

        if (name == null || name.trim().isEmpty()) {
            throw new RuntimeException(
                    "Training name is required."
            );
        }

        // -----------------------------------------------------
        // VALIDATE CATEGORY
        // -----------------------------------------------------

        String trainingCategory =
                category == null || category.trim().isEmpty()
                        ? "Technical"
                        : category.trim();

        if (!isValidCategory(trainingCategory)) {
            throw new RuntimeException(
                    "Invalid training category."
            );
        }

        // -----------------------------------------------------
        // VALIDATE STATUS
        // -----------------------------------------------------

        String trainingStatus =
                status == null || status.trim().isEmpty()
                        ? "DRAFT"
                        : status.trim().toUpperCase();

        if (!isValidStatus(trainingStatus)) {
            throw new RuntimeException(
                    "Invalid training status."
            );
        }

        // -----------------------------------------------------
        // VALIDATE ASSIGNED EMPLOYEES
        // -----------------------------------------------------

        int employees =
                assignedEmployees == null
                        ? 0
                        : assignedEmployees;

        if (employees < 0) {
            throw new RuntimeException(
                    "Assigned employees cannot be negative."
            );
        }

        // -----------------------------------------------------
        // VALIDATE PROGRESS
        // -----------------------------------------------------

        int trainingProgress =
                progress == null
                        ? 0
                        : progress;

        if (trainingProgress < 0 || trainingProgress > 100) {
            throw new RuntimeException(
                    "Progress must be between 0 and 100."
            );
        }

        // -----------------------------------------------------
        // FIND TRAINER
        // -----------------------------------------------------

        User trainer = null;

        if (trainerId != null) {

            trainer = userRepository.findById(trainerId)
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Trainer not found."
                            )
                    );

            if (trainer.getRole() != User.Role.TRAINER) {
                throw new RuntimeException(
                        "Selected user is not a trainer."
                );
            }
        }

        // -----------------------------------------------------
        // CREATE TRAINING
        // -----------------------------------------------------

        Training training = Training.builder()
                .name(name.trim())
                .description(
                        description == null ||
                        description.trim().isEmpty()
                                ? null
                                : description.trim()
                )
                .category(trainingCategory)
                .trainer(trainer)
                .status(trainingStatus)
                .assignedEmployees(employees)
                .progress(trainingProgress)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return trainingRepository.save(training);
    }

    // =========================================================
    // UPDATE TRAINING
    // =========================================================

    @Transactional
    public Training updateTraining(
            Long id,
            String name,
            String description,
            String category,
            Long trainerId,
            String status,
            Integer assignedEmployees,
            Integer progress
    ) {

        Training training = getTraining(id);

        if (name == null || name.trim().isEmpty()) {
            throw new RuntimeException(
                    "Training name is required."
            );
        }

        String trainingCategory =
                category == null || category.trim().isEmpty()
                        ? "Technical"
                        : category.trim();

        if (!isValidCategory(trainingCategory)) {
            throw new RuntimeException(
                    "Invalid training category."
            );
        }

        String trainingStatus =
                status == null || status.trim().isEmpty()
                        ? "DRAFT"
                        : status.trim().toUpperCase();

        if (!isValidStatus(trainingStatus)) {
            throw new RuntimeException(
                    "Invalid training status."
            );
        }

        int employees =
                assignedEmployees == null
                        ? 0
                        : assignedEmployees;

        int trainingProgress =
                progress == null
                        ? 0
                        : progress;

        if (employees < 0) {
            throw new RuntimeException(
                    "Assigned employees cannot be negative."
            );
        }

        if (trainingProgress < 0 || trainingProgress > 100) {
            throw new RuntimeException(
                    "Progress must be between 0 and 100."
            );
        }

        User trainer = null;

        if (trainerId != null) {

            trainer = userRepository.findById(trainerId)
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Trainer not found."
                            )
                    );

            if (trainer.getRole() != User.Role.TRAINER) {
                throw new RuntimeException(
                        "Selected user is not a trainer."
                );
            }
        }

        training.setName(name.trim());

        training.setDescription(
                description == null ||
                description.trim().isEmpty()
                        ? null
                        : description.trim()
        );

        training.setCategory(trainingCategory);
        training.setTrainer(trainer);
        training.setStatus(trainingStatus);
        training.setAssignedEmployees(employees);
        training.setProgress(trainingProgress);
        training.setUpdatedAt(LocalDateTime.now());

        return trainingRepository.save(training);
    }

    // =========================================================
    // UPDATE STATUS
    // =========================================================

    @Transactional
    public Training updateStatus(
            Long id,
            String status
    ) {

        Training training = getTraining(id);

        String newStatus =
                status == null
                        ? ""
                        : status.trim().toUpperCase();

        if (!isValidStatus(newStatus)) {
            throw new RuntimeException(
                    "Invalid training status."
            );
        }

        training.setStatus(newStatus);
        training.setUpdatedAt(LocalDateTime.now());

        return trainingRepository.save(training);
    }

    // =========================================================
    // DELETE TRAINING
    // =========================================================

    @Transactional
    public void deleteTraining(Long id) {

        if (!trainingRepository.existsById(id)) {
            throw new RuntimeException(
                    "Training not found."
            );
        }

        trainingRepository.deleteById(id);
    }

    // =========================================================
    // STATISTICS
    // =========================================================

    @Transactional(readOnly = true)
    public long getTotalTrainings() {
        return trainingRepository.count();
    }

    @Transactional(readOnly = true)
    public long getActiveTrainings() {
        return trainingRepository.countByStatus("ACTIVE");
    }

    @Transactional(readOnly = true)
    public long getCompletedTrainings() {
        return trainingRepository.countByStatus("COMPLETED");
    }

    @Transactional(readOnly = true)
    public long getDraftTrainings() {
        return trainingRepository.countByStatus("DRAFT");
    }

    @Transactional(readOnly = true)
    public long getAssignedEmployees() {

        List<Training> trainings =
                trainingRepository.findAll();

        return trainings.stream()
                .map(Training::getAssignedEmployees)
                .filter(value -> value != null)
                .mapToLong(Integer::longValue)
                .sum();
    }

    // =========================================================
    // VALIDATE CATEGORY
    // =========================================================

    private boolean isValidCategory(String category) {

        return category.equalsIgnoreCase("Technical")
                || category.equalsIgnoreCase("Soft Skills")
                || category.equalsIgnoreCase("Leadership")
                || category.equalsIgnoreCase("Data");
    }

    // =========================================================
    // VALIDATE STATUS
    // =========================================================

    private boolean isValidStatus(String status) {

        return status.equals("ACTIVE")
                || status.equals("COMPLETED")
                || status.equals("DRAFT");
    }
}