package com.capacitix.repository;

import com.capacitix.entity.Competency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CompetencyRepository extends JpaRepository<Competency, Long> {

    List<Competency> findByStatusOrderByNameAsc(String status);

    List<Competency> findAllByOrderByNameAsc();

    long countByStatus(String status);
}