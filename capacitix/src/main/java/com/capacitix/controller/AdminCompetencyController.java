package com.capacitix.controller;

import com.capacitix.entity.Competency;
import com.capacitix.repository.AssessmentAssignmentRepository;
import com.capacitix.repository.CompetencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/competencies")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCompetencyController {

    private final CompetencyRepository competencyRepository;

    private final AssessmentAssignmentRepository
            assessmentAssignmentRepository;


    // =========================================================
    // COMPETENCY LIST
    // =========================================================

    @GetMapping
    public String competencies(
            @RequestParam(required = false) String success,
            Model model) {

        /*
         * =====================================================
         * LOAD COMPETENCIES
         * =====================================================
         */

        List<Competency> competencies =
                competencyRepository
                        .findAllByOrderByNameAsc();


        /*
         * =====================================================
         * LOAD BASIC STATISTICS
         * =====================================================
         */

        long totalCompetencies =
                competencyRepository.count();


        long activeCompetencies =
                competencyRepository
                        .countByStatus("ACTIVE");


        /*
         * =====================================================
         * COUNT UNIQUE CATEGORIES
         * =====================================================
         */

        long skillCategories =
                competencies.stream()
                        .map(Competency::getCategory)
                        .filter(category ->
                                category != null &&
                                !category.trim().isEmpty())
                        .distinct()
                        .count();


        /*
         * =====================================================
         * LOAD REAL MAPPED EMPLOYEE COUNTS
         * =====================================================
         *
         * One database query returns:
         *
         * competency ID → employee count
         *
         * Example:
         *
         * 1 → 2
         * 2 → 5
         * 3 → 1
         */

        List<Object[]> mappedEmployeeRows =
                assessmentAssignmentRepository
                        .findMappedEmployeeCountsByCompetency();


        /*
         * Convert query result into a Map.
         *
         * Key:
         *     competency ID
         *
         * Value:
         *     mapped employee count
         */

        Map<Long, Integer> mappedEmployeeCounts =
                new HashMap<>();


        for (Object[] row : mappedEmployeeRows) {

            if (row == null || row.length < 2) {
                continue;
            }


            Long competencyId =
                    ((Number) row[0]).longValue();


            Integer employeeCount =
                    ((Number) row[1]).intValue();


            mappedEmployeeCounts.put(
                    competencyId,
                    employeeCount
            );
        }


        /*
         * =====================================================
         * APPLY REAL COUNT TO EACH COMPETENCY
         * =====================================================
         */

        for (Competency competency : competencies) {

            if (competency.getId() == null) {

                competency.setMappedEmployees(0);

                continue;
            }


            int mappedCount =
                    mappedEmployeeCounts.getOrDefault(
                            competency.getId(),
                            0
                    );


            competency.setMappedEmployees(
                    mappedCount
            );
        }


        /*
         * =====================================================
         * TOTAL DISTINCT MAPPED EMPLOYEES
         * =====================================================
         *
         * IMPORTANT:
         *
         * We do NOT sum competency counts here.
         *
         * Example:
         *
         * Prashant → Java
         * Prashant → Spring
         *
         * Java = 1
         * Spring = 1
         *
         * But total mapped employees = 1.
         */

        long mappedEmployees =
                assessmentAssignmentRepository
                        .countDistinctMappedEmployees();


        /*
         * =====================================================
         * ADD MODEL ATTRIBUTES
         * =====================================================
         */

        model.addAttribute(
                "competencies",
                competencies
        );


        model.addAttribute(
                "totalCompetencies",
                totalCompetencies
        );


        model.addAttribute(
                "activeCompetencies",
                activeCompetencies
        );


        model.addAttribute(
                "mappedEmployees",
                mappedEmployees
        );


        model.addAttribute(
                "skillCategories",
                skillCategories
        );


        model.addAttribute(
                "userName",
                "Administrator"
        );


        model.addAttribute(
                "userInitial",
                "A"
        );


        /*
         * =====================================================
         * SUCCESS MESSAGES
         * =====================================================
         */

        if ("created".equals(success)) {

            model.addAttribute(
                    "successMessage",
                    "Competency created successfully."
            );

        } else if ("updated".equals(success)) {

            model.addAttribute(
                    "successMessage",
                    "Competency updated successfully."
            );

        } else if ("disabled".equals(success)) {

            model.addAttribute(
                    "successMessage",
                    "Competency disabled successfully."
            );

        } else if ("enabled".equals(success)) {

            model.addAttribute(
                    "successMessage",
                    "Competency enabled successfully."
            );
        }


        return "admin/competencies";
    }


    // =========================================================
    // CREATE PAGE
    // =========================================================

    @GetMapping("/create")
    public String createCompetencyPage(
            Model model) {

        model.addAttribute(
                "competency",
                new Competency()
        );

        model.addAttribute(
                "userName",
                "Administrator"
        );

        model.addAttribute(
                "userInitial",
                "A"
        );

        return "admin/competency-create";
    }


    // =========================================================
    // CREATE
    // =========================================================

    @PostMapping("/create")
    public String createCompetency(
            @RequestParam String name,
            @RequestParam String category,
            @RequestParam String level,
            @RequestParam String status,
            @RequestParam String description,
            Model model) {

        try {

            if (name == null ||
                    name.trim().isEmpty()) {

                throw new RuntimeException(
                        "Competency name is required."
                );
            }


            if (category == null ||
                    category.trim().isEmpty()) {

                throw new RuntimeException(
                        "Category is required."
                );
            }


            if (level == null ||
                    level.trim().isEmpty()) {

                throw new RuntimeException(
                        "Skill level is required."
                );
            }


            if (description == null ||
                    description.trim().isEmpty()) {

                throw new RuntimeException(
                        "Description is required."
                );
            }


            if (status == null ||
                    status.trim().isEmpty()) {

                status = "ACTIVE";
            }


            Competency competency =
                    Competency.builder()
                            .name(name.trim())
                            .description(
                                    description.trim()
                            )
                            .category(
                                    category.trim()
                            )
                            .level(
                                    level.trim()
                            )
                            .status(
                                    status.trim()
                                            .toUpperCase()
                            )
                            .mappedEmployees(0)
                            .build();


            competencyRepository.save(
                    competency
            );


            return "redirect:/admin/competencies?success=created";


        } catch (RuntimeException e) {

            Competency competency =
                    Competency.builder()
                            .name(name)
                            .description(description)
                            .category(category)
                            .level(level)
                            .status(status)
                            .mappedEmployees(0)
                            .build();


            model.addAttribute(
                    "competency",
                    competency
            );


            model.addAttribute(
                    "error",
                    e.getMessage()
            );


            model.addAttribute(
                    "userName",
                    "Administrator"
            );


            model.addAttribute(
                    "userInitial",
                    "A"
            );


            return "admin/competency-create";
        }
    }


    // =========================================================
    // VIEW COMPETENCY
    // =========================================================

    @GetMapping("/{id}")
    public String viewCompetency(
            @PathVariable Long id,
            Model model) {

        Competency competency =
                competencyRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Competency with ID "
                                                + id
                                                + " not found."
                                )
                        );


        model.addAttribute(
                "competency",
                competency
        );


        model.addAttribute(
                "userName",
                "Administrator"
        );


        model.addAttribute(
                "userInitial",
                "A"
        );


        return "admin/competency-view";
    }


    // =========================================================
    // EDIT PAGE
    // =========================================================

    @GetMapping("/{id}/edit")
    public String editCompetency(
            @PathVariable Long id,
            Model model) {

        Competency competency =
                competencyRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Competency with ID "
                                                + id
                                                + " not found."
                                )
                        );


        model.addAttribute(
                "competency",
                competency
        );


        model.addAttribute(
                "userName",
                "Administrator"
        );


        model.addAttribute(
                "userInitial",
                "A"
        );


        return "admin/competency-edit";
    }


    // =========================================================
    // UPDATE COMPETENCY
    // =========================================================

    @PostMapping("/{id}/edit")
    public String updateCompetency(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam String category,
            @RequestParam String level,
            @RequestParam String status,
            @RequestParam String description) {

        Competency competency =
                competencyRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Competency with ID "
                                                + id
                                                + " not found."
                                )
                        );


        competency.setName(
                name.trim()
        );


        competency.setCategory(
                category.trim()
        );


        competency.setLevel(
                level.trim()
        );


        competency.setStatus(
                status.trim().toUpperCase()
        );


        competency.setDescription(
                description.trim()
        );


        /*
         * mappedEmployees is intentionally NOT changed here.
         *
         * It is calculated dynamically from assignments.
         */


        competencyRepository.save(
                competency
        );


        return "redirect:/admin/competencies?success=updated";
    }


    // =========================================================
    // DISABLE
    // =========================================================

    @PostMapping("/{id}/disable")
    public String disableCompetency(
            @PathVariable Long id) {

        Competency competency =
                competencyRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Competency with ID "
                                                + id
                                                + " not found."
                                )
                        );


        competency.setStatus("INACTIVE");


        competencyRepository.save(
                competency
        );


        return "redirect:/admin/competencies?success=disabled";
    }


    // =========================================================
    // ENABLE
    // =========================================================

    @PostMapping("/{id}/enable")
    public String enableCompetency(
            @PathVariable Long id) {

        Competency competency =
                competencyRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Competency with ID "
                                                + id
                                                + " not found."
                                )
                        );


        competency.setStatus("ACTIVE");


        competencyRepository.save(
                competency
        );


        return "redirect:/admin/competencies?success=enabled";
    }
}