package com.capacitix.service;

import com.capacitix.entity.OrganizationSettings;
import com.capacitix.repository.OrganizationSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class OrganizationSettingsService {

    private final OrganizationSettingsRepository organizationSettingsRepository;


    // =========================================================
    // GET SETTINGS
    // =========================================================

    public OrganizationSettings getSettings() {

        return organizationSettingsRepository
                .findTopByOrderByIdAsc()
                .orElseGet(this::createDefaultSettings);
    }


    // =========================================================
    // SAVE ORGANIZATION SETTINGS
    // =========================================================

    public OrganizationSettings saveOrganizationSettings(
            String organizationName,
            String organizationType,
            String contactEmail,
            String contactPhone,
            String address) {

        OrganizationSettings settings = getSettings();

        settings.setOrganizationName(cleanRequired(
                organizationName,
                "Organization name is required."
        ));

        settings.setOrganizationType(cleanOptional(organizationType));
        settings.setContactEmail(cleanOptional(contactEmail));
        settings.setContactPhone(cleanOptional(contactPhone));
        settings.setAddress(cleanOptional(address));

        return organizationSettingsRepository.save(settings);
    }


    // =========================================================
    // SAVE NOTIFICATION SETTINGS
    // =========================================================

    public OrganizationSettings saveNotificationSettings(
            boolean notifyEmployeeInvitation,
            boolean notifyTrainingCompletion,
            boolean notifyAssessmentCompletion,
            boolean notifySkillGap,
            boolean notifyWeeklyReports) {

        OrganizationSettings settings = getSettings();

        settings.setNotifyEmployeeInvitation(notifyEmployeeInvitation);
        settings.setNotifyTrainingCompletion(notifyTrainingCompletion);
        settings.setNotifyAssessmentCompletion(notifyAssessmentCompletion);
        settings.setNotifySkillGap(notifySkillGap);
        settings.setNotifyWeeklyReports(notifyWeeklyReports);

        return organizationSettingsRepository.save(settings);
    }


    // =========================================================
    // SAVE SYSTEM SETTINGS
    // =========================================================

    public OrganizationSettings saveSystemSettings(
            String defaultLanguage,
            String timeZone,
            String dateFormat,
            String dashboardRefresh) {

        OrganizationSettings settings = getSettings();

        settings.setDefaultLanguage(cleanOptional(defaultLanguage));
        settings.setTimeZone(cleanOptional(timeZone));
        settings.setDateFormat(cleanOptional(dateFormat));
        settings.setDashboardRefresh(cleanOptional(dashboardRefresh));

        return organizationSettingsRepository.save(settings);
    }


    // =========================================================
    // DISABLE ORGANIZATION
    // =========================================================

    public OrganizationSettings disableOrganization() {

        OrganizationSettings settings = getSettings();

        settings.setEnabled(false);

        return organizationSettingsRepository.save(settings);
    }


    // =========================================================
    // ENABLE ORGANIZATION
    // =========================================================

    public OrganizationSettings enableOrganization() {

        OrganizationSettings settings = getSettings();

        settings.setEnabled(true);

        return organizationSettingsRepository.save(settings);
    }


    // =========================================================
    // CREATE DEFAULT SETTINGS
    // =========================================================

    private OrganizationSettings createDefaultSettings() {

        OrganizationSettings settings = OrganizationSettings.builder()
                .organizationName("Capacitix Organization")
                .organizationType("Corporate")
                .contactEmail("admin@capacitix.com")
                .contactPhone(null)
                .address(null)

                .notifyEmployeeInvitation(true)
                .notifyTrainingCompletion(true)
                .notifyAssessmentCompletion(true)
                .notifySkillGap(true)
                .notifyWeeklyReports(false)

                .defaultLanguage("English")
                .timeZone("Asia/Kolkata")
                .dateFormat("DD/MM/YYYY")
                .dashboardRefresh("Every 5 Minutes")

                .enabled(true)
                .build();

        return organizationSettingsRepository.save(settings);
    }


    // =========================================================
    // CLEAN OPTIONAL VALUE
    // =========================================================

    private String cleanOptional(String value) {

        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        return value.trim();
    }


    // =========================================================
    // CLEAN REQUIRED VALUE
    // =========================================================

    private String cleanRequired(String value, String message) {

        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }

        return value.trim();
    }
}