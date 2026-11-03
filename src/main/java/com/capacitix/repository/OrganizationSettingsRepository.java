package com.capacitix.repository;

import com.capacitix.entity.OrganizationSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganizationSettingsRepository
        extends JpaRepository<OrganizationSettings, Long> {

    Optional<OrganizationSettings> findTopByOrderByIdAsc();
}