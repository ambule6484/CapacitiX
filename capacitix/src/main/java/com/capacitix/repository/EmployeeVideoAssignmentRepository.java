package com.capacitix.repository;

import com.capacitix.entity.EmployeeVideoAssignment;
import com.capacitix.entity.TrainingVideo;
import com.capacitix.entity.User;
import com.capacitix.entity.VideoAssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeVideoAssignmentRepository
        extends JpaRepository<EmployeeVideoAssignment, Long> {

    /**
     * Find all learning resources assigned to an employee.
     *
     * Works for:
     * - Video
     * - Image
     * - PDF
     * - PowerPoint
     * - Word
     */
    List<EmployeeVideoAssignment> findByEmployeeOrderByAssignedAtDesc(
            User employee
    );

    /**
     * Find assignments of a particular status for an employee.
     *
     * Example:
     * ASSIGNED
     * IN_PROGRESS
     * COMPLETED
     */
    List<EmployeeVideoAssignment> findByEmployeeAndStatusOrderByAssignedAtDesc(
            User employee,
            VideoAssignmentStatus status
    );

    /**
     * Find all resource assignments created by a trainer.
     */
    List<EmployeeVideoAssignment> findByTrainerOrderByAssignedAtDesc(
            User trainer
    );

    /**
     * Check whether a resource is already assigned
     * to a particular employee.
     */
    boolean existsByVideoAndEmployee(
            TrainingVideo video,
            User employee
    );

    /**
     * Find a particular resource assignment for an employee.
     */
    Optional<EmployeeVideoAssignment> findByVideoAndEmployee(
            TrainingVideo video,
            User employee
    );

    /**
     * Find an assignment by ID belonging to a particular employee.
     *
     * This prevents one employee from accessing
     * another employee's assignment.
     */
    Optional<EmployeeVideoAssignment> findByIdAndEmployee(
            Long id,
            User employee
    );

    /**
     * Find all assignments for a particular resource.
     */
    List<EmployeeVideoAssignment> findByVideoOrderByAssignedAtDesc(
            TrainingVideo video
    );

    /**
     * Count how many employees have been assigned
     * a particular resource.
     */
    long countByVideo(
            TrainingVideo video
    );

    /**
     * Count assignments for a trainer.
     */
    long countByTrainer(
            User trainer
    );

    /**
     * Count assignments for an employee.
     */
    long countByEmployee(
            User employee
    );
}