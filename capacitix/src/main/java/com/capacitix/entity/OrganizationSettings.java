
package com.capacitix.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "organization_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationSettings {

    /*
     * =========================================================
     * PRIMARY KEY
     * =========================================================
     */

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /*
     * =========================================================
     * ORGANIZATION INFORMATION
     * =========================================================
     */

    @Column(name = "organization_name", nullable = false, length = 150)
    @Builder.Default
    private String organizationName = "Capacitix Organization";

    @Column(name = "organization_type", length = 100)
    @Builder.Default
    private String organizationType = "Corporate";

    @Column(name = "contact_email", length = 150)
    private String contactEmail;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Column(name = "address", length = 500)
    private String address;


    /*
     * =========================================================
     * NOTIFICATION SETTINGS
     * =========================================================
     */

    @Column(name = "notify_employee_invitation", nullable = false)
    @Builder.Default
    private boolean notifyEmployeeInvitation = true;

    @Column(name = "notify_training_completion", nullable = false)
    @Builder.Default
    private boolean notifyTrainingCompletion = true;

    @Column(name = "notify_assessment_completion", nullable = false)
    @Builder.Default
    private boolean notifyAssessmentCompletion = true;

    @Column(name = "notify_skill_gap", nullable = false)
    @Builder.Default
    private boolean notifySkillGap = true;

    @Column(name = "notify_weekly_reports", nullable = false)
    @Builder.Default
    private boolean notifyWeeklyReports = false;


    /*
     * =========================================================
     * SYSTEM SETTINGS
     * =========================================================
     */

    @Column(name = "default_language", length = 50)
    @Builder.Default
    private String defaultLanguage = "English";

    @Column(name = "time_zone", length = 100)
    @Builder.Default
    private String timeZone = "Asia/Kolkata";

    @Column(name = "date_format", length = 30)
    @Builder.Default
    private String dateFormat = "DD/MM/YYYY";

    @Column(name = "dashboard_refresh", length = 50)
    @Builder.Default
    private String dashboardRefresh = "Every 5 Minutes";


    /*
     * =========================================================
     * ORGANIZATION STATUS
     * =========================================================
     *
     * true  = organization is active
     * false = organization is disabled
     */

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;
}
