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
     * Assign a training resource to an employee.
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

        if (employee == null || employee.getId() == null) {
            throw new IllegalArgumentException(
                    "Valid employee is required."
            );
        }

        if (trainer == null || trainer.getId() == null) {
            throw new IllegalArgumentException(
                    "Valid trainer is required."
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

        if (!video.isActive()) {
            throw new IllegalArgumentException(
                    "This training resource is no longer active."
            );
        }

        if (video.getTrainer() == null
                || video.getTrainer().getId() == null
                || !video.getTrainer().getId().equals(trainer.getId())) {

            throw new SecurityException(
                    "You are not authorized to assign this resource."
            );
        }

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
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
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
     * Get assignments for a particular resource.
     *
     * Employee is fetched using EntityGraph in the repository.
     * Therefore employee details remain accessible after this
     * transactional method returns.
     */
    @Transactional(readOnly = true)
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
     * Get a particular assignment belonging to an employee.
     */
    @Transactional(readOnly = true)
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
                .findByIdAndEmployee(assignmentId, employee)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Video assignment not found."
                        )
                );
    }

    /**
     * Update resource progress.
     */
    @Transactional
    public EmployeeVideoAssignment updateProgress(
            Long assignmentId,
            User employee,
            Integer progress
    ) {

        if (progress == null) {
            throw new IllegalArgumentException(
                    "Progress value is required."
            );
        }

        EmployeeVideoAssignment assignment =
                getEmployeeAssignment(assignmentId, employee);

        progress = Math.max(0, Math.min(100, progress));

        assignment.setProgressPercentage(progress);

        if (progress >= 100) {

            assignment.setProgressPercentage(100);

            assignment.setStatus(
                    VideoAssignmentStatus.COMPLETED
            );

            assignment.setCompletedAt(LocalDateTime.now());

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
                getEmployeeAssignment(assignmentId, employee);

        assignment.setProgressPercentage(100);

        assignment.setStatus(
                VideoAssignmentStatus.COMPLETED
        );

        assignment.setCompletedAt(LocalDateTime.now());

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

        if (trainer == null || trainer.getId() == null) {
            throw new IllegalArgumentException(
                    "Valid trainer is required."
            );
        }

        EmployeeVideoAssignment assignment =
                assignmentRepository.findById(assignmentId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Video assignment not found."
                                )
                        );

        if (assignment.getTrainer() == null
                || assignment.getTrainer().getId() == null
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