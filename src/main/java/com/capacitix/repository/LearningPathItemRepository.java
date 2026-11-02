package com.capacitix.repository;

import com.capacitix.entity.LearningPath;
import com.capacitix.entity.LearningPathItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LearningPathItemRepository
        extends JpaRepository<LearningPathItem, Long> {


    // =========================================================
    // GET ITEMS BY LEARNING PATH
    // =========================================================

    List<LearningPathItem>
    findByLearningPathOrderBySequenceOrderAsc(
            LearningPath learningPath
    );


    // =========================================================
    // GET ITEM BY SEQUENCE
    // =========================================================

    Optional<LearningPathItem>
    findByLearningPathAndSequenceOrder(
            LearningPath learningPath,
            Integer sequenceOrder
    );


    // =========================================================
    // GET ITEMS BY TARGET SKILL
    // =========================================================

    List<LearningPathItem>
    findByLearningPathAndTargetSkillIgnoreCase(
            LearningPath learningPath,
            String targetSkill
    );


    // =========================================================
    // GET ITEMS BY STATUS
    // =========================================================

    List<LearningPathItem>
    findByLearningPathAndStatus(
            LearningPath learningPath,
            LearningPathItem.ItemStatus status
    );


    // =========================================================
    // GET ITEMS BY PRIORITY
    // =========================================================

    List<LearningPathItem>
    findByLearningPathAndPriority(
            LearningPath learningPath,
            LearningPathItem.Priority priority
    );


    // =========================================================
    // COUNT ITEMS
    // =========================================================

    long countByLearningPath(
            LearningPath learningPath
    );


    // =========================================================
    // COUNT BY STATUS
    // =========================================================

    long countByLearningPathAndStatus(
            LearningPath learningPath,
            LearningPathItem.ItemStatus status
    );


    // =========================================================
    // DELETE ITEMS
    // =========================================================

    void deleteByLearningPath(
            LearningPath learningPath
    );
}