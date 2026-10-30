package com.capacitix.repository;

import com.capacitix.entity.ResourceType;
import com.capacitix.entity.TrainingVideo;
import com.capacitix.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrainingVideoRepository extends JpaRepository<TrainingVideo, Long> {

    /**
     * Find all active learning resources.
     */
    List<TrainingVideo> findByActiveTrueOrderByUploadedAtDesc();

    /**
     * Find all learning resources uploaded by a particular trainer.
     */
    List<TrainingVideo> findByTrainerOrderByUploadedAtDesc(User trainer);

    /**
     * Find active learning resources uploaded by a particular trainer.
     */
    List<TrainingVideo> findByTrainerAndActiveTrueOrderByUploadedAtDesc(User trainer);

    /**
     * Find active resources of a particular type.
     *
     * Example:
     * VIDEO, IMAGE, PDF, PPT, WORD
     */
    List<TrainingVideo> findByResourceTypeAndActiveTrueOrderByUploadedAtDesc(
            ResourceType resourceType
    );

    /**
     * Find resources of a particular type uploaded by a trainer.
     */
    List<TrainingVideo> findByTrainerAndResourceTypeOrderByUploadedAtDesc(
            User trainer,
            ResourceType resourceType
    );

    /**
     * Find active resources of a particular type uploaded by a trainer.
     */
    List<TrainingVideo> findByTrainerAndResourceTypeAndActiveTrueOrderByUploadedAtDesc(
            User trainer,
            ResourceType resourceType
    );

    /**
     * Count active resources uploaded by a trainer.
     */
    long countByTrainerAndActiveTrue(User trainer);

    /**
     * Count active resources of a particular type uploaded by a trainer.
     */
    long countByTrainerAndResourceTypeAndActiveTrue(
            User trainer,
            ResourceType resourceType
    );
}