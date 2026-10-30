package com.capacitix.service;

import com.capacitix.entity.EmployeeVideoAssignment;
import com.capacitix.entity.TrainingVideo;
import com.capacitix.entity.User;
import com.capacitix.entity.VideoAssignmentStatus;
import com.capacitix.repository.EmployeeVideoAssignmentRepository;
import com.capacitix.repository.TrainingVideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmployeeVideoAssignmentService {

    private final EmployeeVideoAssignmentRepository assignmentRepository;
    private final TrainingVideoRepository trainingVideoRepository;

    /**
     * Assign a training resource to a particular employee.
     */
    @Transactional
    public EmployeeVideoAssignment assignVideoToEmployee(
            Long videoId,
            User employee,
            User trainer
    ) {

        if (videoId == null) {
            throw new IllegalArgumentException(
                    "Training resource ID is required."
            );
        }

        if (employee == null) {
            throw new IllegalArgumentException(
                    "Employee is required."
            );
        }

        if (trainer == null) {
            throw new IllegalArgumentException(
                    "Trainer is required."
            );
        }

        TrainingVideo video =
                trainingVideoRepository.findById(videoId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Training resource not found with ID: "
                                                + videoId
                                )
                        );

        // Resource must be active
        if (!video.isActive()) {
            throw new IllegalArgumentException(
                    "This training resource is no longer active."
            );
        }

        // Make sure the trainer owns this resource
        if (video.getTrainer() == null
                || video.getTrainer().getId() == null
                || trainer.getId() == null
                || !video.getTrainer()
                        .getId()
                        .equals(trainer.getId())) {

            throw new SecurityException(
                    "You are not authorized to assign this resource."
            );
        }

        // Prevent duplicate assignment
        if (assignmentRepository.existsByVideoAndEmployee(
                video,
                employee
        )) {
            throw new IllegalArgumentException(
                    "This resource is already assigned to this employee."
            );
        }

        EmployeeVideoAssignment assignment =
                EmployeeVideoAssignment.builder()
                        .video(video)
                        .employee(employee)
                        .trainer(trainer)
                        .assignedAt(LocalDateTime.now())
                        .status(VideoAssignmentStatus.ASSIGNED)
                        .progressPercentage(0)
                        .build();

        return assignmentRepository.save(assignment);
    }

    /**
     * Get all resources assigned to an employee.
     */
    public List<EmployeeVideoAssignment> getEmployeeVideos(
            User employee
    ) {

        if (employee == null) {
            return List.of();
        }

        return assignmentRepository
                .findByEmployeeOrderByAssignedAtDesc(employee);
    }

    /**
     * Get all assignments created by a trainer.
     */
    public List<EmployeeVideoAssignment> getTrainerAssignments(
            User trainer
    ) {

        if (trainer == null) {
            return List.of();
        }

        return assignmentRepository
                .findByTrainerOrderByAssignedAtDesc(trainer);
    }

    /**
     * Get all employees assigned to a particular resource.
     *
     * Used by the Trainer Learning Resources page to display
     * which employees have received each resource.
     */
    public List<EmployeeVideoAssignment> getTrainerAssignmentsForResource(
            TrainingVideo video
    ) {

        if (video == null) {
            return List.of();
        }

        return assignmentRepository
                .findByVideoOrderByAssignedAtDesc(video);
    }

    /**
     * Get a particular assignment for an employee.
     */
    public EmployeeVideoAssignment getEmployeeAssignment(
            Long assignmentId,
            User employee
    ) {

        if (assignmentId == null) {
            throw new IllegalArgumentException(
                    "Assignment ID is required."
            );
        }

        if (employee == null) {
            throw new IllegalArgumentException(
                    "Employee is required."
            );
        }

        return assignmentRepository
                .findByIdAndEmployee(
                        assignmentId,
                        employee
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Video assignment not found."
                        )
                );
    }

    /**
     * Update video/resource progress.
     */
    @Transactional
    public EmployeeVideoAssignment updateProgress(
            Long assignmentId,
            User employee,
            Integer progress
    ) {

        EmployeeVideoAssignment assignment =
                getEmployeeAssignment(
                        assignmentId,
                        employee
                );

        if (progress == null) {
            throw new IllegalArgumentException(
                    "Progress value is required."
            );
        }

        // Keep progress between 0 and 100
        progress =
                Math.max(
                        0,
                        Math.min(
                                100,
                                progress
                        )
                );

        assignment.setProgressPercentage(progress);

        if (progress >= 100) {

            assignment.setProgressPercentage(100);

            assignment.setStatus(
                    VideoAssignmentStatus.COMPLETED
            );

            assignment.setCompletedAt(
                    LocalDateTime.now()
            );

        } else if (progress > 0) {

            assignment.setStatus(
                    VideoAssignmentStatus.IN_PROGRESS
            );

            assignment.setCompletedAt(null);

        } else {

            assignment.setStatus(
                    VideoAssignmentStatus.ASSIGNED
            );

            assignment.setCompletedAt(null);
        }

        return assignmentRepository.save(assignment);
    }

    /**
     * Mark a resource as completed.
     */
    @Transactional
    public EmployeeVideoAssignment markCompleted(
            Long assignmentId,
            User employee
    ) {

        EmployeeVideoAssignment assignment =
                getEmployeeAssignment(
                        assignmentId,
                        employee
                );

        assignment.setProgressPercentage(100);

        assignment.setStatus(
                VideoAssignmentStatus.COMPLETED
        );

        assignment.setCompletedAt(
                LocalDateTime.now()
        );

        return assignmentRepository.save(assignment);
    }

    /**
     * Delete an assignment created by a trainer.
     */
    @Transactional
    public void removeAssignment(
            Long assignmentId,
            User trainer
    ) {

        if (assignmentId == null) {
            throw new IllegalArgumentException(
                    "Assignment ID is required."
            );
        }

        if (trainer == null) {
            throw new IllegalArgumentException(
                    "Trainer is required."
            );
        }

        EmployeeVideoAssignment assignment =
                assignmentRepository.findById(
                        assignmentId
                ).orElseThrow(() ->
                        new IllegalArgumentException(
                                "Video assignment not found."
                        )
                );

        // Only the trainer who created the assignment
        // can remove it.
        if (assignment.getTrainer() == null
                || assignment.getTrainer().getId() == null
                || trainer.getId() == null
                || !assignment.getTrainer()
                        .getId()
                        .equals(trainer.getId())) {

            throw new SecurityException(
                    "You are not authorized to remove this assignment."
            );
        }

        assignmentRepository.delete(assignment);
    }
}