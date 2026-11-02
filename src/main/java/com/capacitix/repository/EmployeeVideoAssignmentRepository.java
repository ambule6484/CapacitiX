package com.capacitix.repository;

import com.capacitix.entity.EmployeeVideoAssignment;
import com.capacitix.entity.TrainingVideo;
import com.capacitix.entity.User;
import com.capacitix.entity.VideoAssignmentStatus;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeVideoAssignmentRepository
        extends JpaRepository<EmployeeVideoAssignment, Long> {

    @EntityGraph(attributePaths = {"employee"})
    List<EmployeeVideoAssignment> findByEmployeeOrderByAssignedAtDesc(
            User employee
    );

    @EntityGraph(attributePaths = {"employee"})
    List<EmployeeVideoAssignment> findByEmployeeAndStatusOrderByAssignedAtDesc(
            User employee,
            VideoAssignmentStatus status
    );

    @EntityGraph(attributePaths = {"employee"})
    List<EmployeeVideoAssignment> findByTrainerOrderByAssignedAtDesc(
            User trainer
    );

    boolean existsByVideoAndEmployee(
            TrainingVideo video,
            User employee
    );

    Optional<EmployeeVideoAssignment> findByVideoAndEmployee(
            TrainingVideo video,
            User employee
    );

    @EntityGraph(attributePaths = {"employee"})
    Optional<EmployeeVideoAssignment> findByIdAndEmployee(
            Long id,
            User employee
    );

    /**
     * Important:
     * Fetch employee together with assignments.
     * Prevents LazyInitializationException when the
     * trainer resource page accesses employee.getFullName().
     */
    @EntityGraph(attributePaths = {"employee"})
    List<EmployeeVideoAssignment> findByVideoOrderByAssignedAtDesc(
            TrainingVideo video
    );

    long countByVideo(TrainingVideo video);

    long countByTrainer(User trainer);

    long countByEmployee(User employee);
}