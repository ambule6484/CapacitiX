package com.capacitix.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "learning_path_items",
        uniqueConstraints = {

                @UniqueConstraint(
                        name = "uk_learning_path_training_module",
                        columnNames = {
                                "learning_path_id",
                                "training_module_id"
                        }
                ),

                @UniqueConstraint(
                        name = "uk_learning_path_sequence",
                        columnNames = {
                                "learning_path_id",
                                "sequence_order"
                        }
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearningPathItem {

    // =========================================================
    // PRIMARY KEY
    // =========================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // =========================================================
    // LEARNING PATH
    // =========================================================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "learning_path_id",
            nullable = false
    )
    private LearningPath learningPath;


    // =========================================================
    // TRAINING MODULE
    // =========================================================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "training_module_id",
            nullable = false
    )
    private TrainingModule trainingModule;


    // =========================================================
    // SEQUENCE
    // =========================================================

    @Column(
            name = "sequence_order",
            nullable = false
    )
    private Integer sequenceOrder;


    // =========================================================
    // TARGET SKILL
    // =========================================================

    @Column(
            name = "target_skill",
            length = 150
    )
    private String targetSkill;


    // =========================================================
    // RECOMMENDATION REASON
    // =========================================================

    @Column(
            name = "recommendation_reason",
            columnDefinition = "TEXT"
    )
    private String recommendationReason;


    // =========================================================
    // PRIORITY
    // =========================================================

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20
    )
    @Builder.Default
    private Priority priority = Priority.MEDIUM;


    // =========================================================
    // STATUS
    // =========================================================

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20
    )
    @Builder.Default
    private ItemStatus status = ItemStatus.NOT_STARTED;


    // =========================================================
    // PROGRESS
    // =========================================================

    @Column(
            nullable = false
    )
    @Builder.Default
    private Integer progressPercentage = 0;


    // =========================================================
    // ENUM: PRIORITY
    // =========================================================

    public enum Priority {
        LOW,
        MEDIUM,
        HIGH
    }


    // =========================================================
    // ENUM: ITEM STATUS
    // =========================================================

    public enum ItemStatus {
        NOT_STARTED,
        IN_PROGRESS,
        COMPLETED
    }
}