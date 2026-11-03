package com.capacitix.repository;

import com.capacitix.entity.TrainerInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TrainerInvitationRepository
        extends JpaRepository<TrainerInvitation, Long> {

    /*
     * =========================================================
     * FIND INVITATION BY SECURE TOKEN
     * =========================================================
     */
    Optional<TrainerInvitation> findByToken(String token);


    /*
     * =========================================================
     * CHECK TOKEN EXISTS
     * =========================================================
     */
    boolean existsByToken(String token);


    /*
     * =========================================================
     * FIND INVITATION BY EMAIL
     * =========================================================
     */
    Optional<TrainerInvitation> findByEmail(String email);


    /*
     * =========================================================
     * COUNT PENDING INVITATIONS
     * =========================================================
     *
     * Pending invitation means:
     *
     * used = false
     * AND
     * expires_at is still in the future
     *
     * This gives the Admin Dashboard a real database value.
     */
    long countByUsedFalseAndExpiresAtAfter(LocalDateTime currentTime);


    /*
     * =========================================================
     * FIND PENDING INVITATIONS
     * =========================================================
     *
     * Useful for the Admin Invitations page.
     */
    List<TrainerInvitation> findByUsedFalseAndExpiresAtAfterOrderByExpiresAtAsc(
            LocalDateTime currentTime
    );


    /*
     * =========================================================
     * COUNT USED INVITATIONS
     * =========================================================
     *
     * Used = trainer has already accepted the invitation
     * and created the account/password.
     */
    long countByUsedTrue();


    /*
     * =========================================================
     * COUNT EXPIRED UNUSED INVITATIONS
     * =========================================================
     */
    long countByUsedFalseAndExpiresAtBefore(LocalDateTime currentTime);
}