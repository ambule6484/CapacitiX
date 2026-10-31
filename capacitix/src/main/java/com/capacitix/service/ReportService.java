package com.capacitix.service;

import com.capacitix.entity.Report;
import com.capacitix.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;

    public List<Report> getAllReports() {
        return reportRepository.findAllByOrderByGeneratedDateDesc();
    }

    public Report getReport(Long id) {
        return reportRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Report not found with ID: " + id)
                );
    }

    @Transactional
    public Report generateReport(
            String name,
            String description,
            String type
    ) {

        if (name == null || name.trim().isEmpty()) {
            throw new RuntimeException("Report name is required.");
        }

        if (type == null || type.trim().isEmpty()) {
            throw new RuntimeException("Report type is required.");
        }

        String reportType = type.trim();

        if (!isValidType(reportType)) {
            throw new RuntimeException("Invalid report type.");
        }

        Report report = Report.builder()
                .name(name.trim())
                .description(
                        description == null || description.trim().isEmpty()
                                ? null
                                : description.trim()
                )
                .type(reportType)
                .generatedBy("Administrator")
                .generatedDate(LocalDateTime.now())
                .status("READY")
                .build();

        return reportRepository.save(report);
    }

    public long getTotalReports() {
        return reportRepository.count();
    }

    public long getTrainingReports() {
        return reportRepository.countByType("Training");
    }

    public long getSkillGapReports() {
        return reportRepository.countByType("Skill Gap");
    }

    public long getAssessmentReports() {
        return reportRepository.countByType("Assessment");
    }

    public long getEmployeeReports() {
        return reportRepository.countByType("Employee");
    }

    public long getCompetencyReports() {
        return reportRepository.countByType("Competency");
    }

    public long getTrainingImpactReports() {
        return reportRepository.countByType("Training Impact");
    }

    @Transactional
    public void deleteReport(Long id) {

        if (!reportRepository.existsById(id)) {
            throw new RuntimeException("Report not found.");
        }

        reportRepository.deleteById(id);
    }

    private boolean isValidType(String type) {

        return type.equalsIgnoreCase("Training")
                || type.equalsIgnoreCase("Skill Gap")
                || type.equalsIgnoreCase("Assessment")
                || type.equalsIgnoreCase("Employee")
                || type.equalsIgnoreCase("Competency")
                || type.equalsIgnoreCase("Training Impact");
    }
}