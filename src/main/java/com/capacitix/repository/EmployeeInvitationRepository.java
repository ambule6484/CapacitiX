
package com.capacitix.repository;

import com.capacitix.entity.EmployeeInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EmployeeInvitationRepository
        extends JpaRepository<EmployeeInvitation, Long> {

    Optional<EmployeeInvitation> findByToken(String token);

    boolean existsByToken(String token);

    Optional<EmployeeInvitation> findByEmail(String email);

    long countByUsedFalseAndExpiresAtAfter(
            LocalDateTime currentTime
    );

    List<EmployeeInvitation>
    findByUsedFalseAndExpiresAtAfterOrderByExpiresAtAsc(
            LocalDateTime currentTime
    );

    long countByUsedTrue();

    long countByUsedFalseAndExpiresAtBefore(
            LocalDateTime currentTime
    );
}