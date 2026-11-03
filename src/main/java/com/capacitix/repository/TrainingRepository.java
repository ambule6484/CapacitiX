package com.capacitix.repository;

import com.capacitix.entity.Training;
import com.capacitix.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrainingRepository extends JpaRepository<Training, Long> {

    List<Training> findAllByOrderByCreatedAtDesc();

    List<Training> findByStatusOrderByCreatedAtDesc(String status);

    List<Training> findByCategoryOrderByCreatedAtDesc(String category);

    List<Training> findByTrainerOrderByCreatedAtDesc(User trainer);

    long countByStatus(String status);

    long countByTrainer(User trainer);

    long countByAssignedEmployeesGreaterThan(Integer count);
}