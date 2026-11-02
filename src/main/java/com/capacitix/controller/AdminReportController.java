package com.capacitix.controller;

import com.capacitix.entity.Report;
import com.capacitix.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminReportController {

    private final ReportService reportService;

    /*
     * =========================================================
     * REPORTS LIST
     * URL: /admin/reports
     * =========================================================
     */
    @GetMapping
    public String reports(Model model) {

        model.addAttribute(
                "reports",
                reportService.getAllReports()
        );

        model.addAttribute(
                "totalReports",
                reportService.getTotalReports()
        );

        model.addAttribute(
                "trainingReports",
                reportService.getTrainingReports()
        );

        model.addAttribute(
                "skillGapReports",
                reportService.getSkillGapReports()
        );

        model.addAttribute(
                "assessmentReports",
                reportService.getAssessmentReports()
        );

        model.addAttribute(
                "userName",
                "Administrator"
        );

        model.addAttribute(
                "userInitial",
                "A"
        );

        return "admin/reports";
    }


    /*
     * =========================================================
     * GENERATE REPORT PAGE
     * URL: /admin/reports/generate
     * =========================================================
     */
    @GetMapping("/generate")
    public String generateReportPage(Model model) {

        model.addAttribute(
                "userName",
                "Administrator"
        );

        model.addAttribute(
                "userInitial",
                "A"
        );

        return "admin/report-create";
    }


    /*
     * =========================================================
     * GENERATE REPORT
     * POST: /admin/reports/generate
     * =========================================================
     */
    @PostMapping("/generate")
    public String generateReport(
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam String type,
            Model model
    ) {

        try {

            reportService.generateReport(
                    name,
                    description,
                    type
            );

            return "redirect:/admin/reports?success=generated";

        } catch (RuntimeException e) {

            model.addAttribute(
                    "error",
                    e.getMessage()
            );

            model.addAttribute(
                    "name",
                    name
            );

            model.addAttribute(
                    "description",
                    description
            );

            model.addAttribute(
                    "type",
                    type
            );

            model.addAttribute(
                    "userName",
                    "Administrator"
            );

            model.addAttribute(
                    "userInitial",
                    "A"
            );

            return "admin/report-create";
        }
    }


    /*
     * =========================================================
     * VIEW REPORT
     * URL: /admin/reports/{id}
     * =========================================================
     */
    @GetMapping("/{id}")
    public String viewReport(
            @PathVariable Long id,
            Model model
    ) {

        Report report =
                reportService.getReport(id);

        model.addAttribute(
                "report",
                report
        );

        model.addAttribute(
                "userName",
                "Administrator"
        );

        model.addAttribute(
                "userInitial",
                "A"
        );

        return "admin/report-view";
    }


    /*
     * =========================================================
     * DELETE REPORT
     * POST: /admin/reports/{id}/delete
     * =========================================================
     */
    @PostMapping("/{id}/delete")
    public String deleteReport(
            @PathVariable Long id
    ) {

        reportService.deleteReport(id);

        return "redirect:/admin/reports?success=deleted";
    }
}