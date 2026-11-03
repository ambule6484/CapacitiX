package com.capacitix.repository;

import com.capacitix.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findAllByOrderByGeneratedDateDesc();

    long countByType(String type);

    long countByStatus(String status);
}