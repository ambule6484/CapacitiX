package com.capacitix.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "training_modules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainingModule {

    // =========================================================
    // PRIMARY KEY
    // =========================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // =========================================================
    // TITLE
    // =========================================================

    @Column(
            nullable = false,
            length = 200
    )
    private String title;


    // =========================================================
    // DESCRIPTION
    // =========================================================

    @Column(
            columnDefinition = "TEXT"
    )
    private String description;


    // =========================================================
    // SKILL
    // =========================================================

    @Column(
            name = "skill_name",
            nullable = false,
            length = 150
    )
    private String skillName;


    // =========================================================
    // LEARNING PATH ITEM
    // =========================================================

    /**
     * Links this personalized training module to the
     * exact learning-path item.
     *
     * The learning-path item is employee-specific through
     * its associated LearningPath.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "learning_path_item_id"
    )
    private LearningPathItem learningPathItem;


    // =========================================================
    // DIFFICULTY
    // =========================================================

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20
    )
    @Builder.Default
    private DifficultyLevel difficultyLevel =
            DifficultyLevel.BEGINNER;


    // =========================================================
    // MODULE TYPE
    // =========================================================

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 30
    )
    @Builder.Default
    private ModuleType moduleType =
            ModuleType.ARTICLE;


    // =========================================================
    // CONTENT
    // =========================================================

    @Column(
            columnDefinition = "LONGTEXT"
    )
    private String content;


    // =========================================================
    // RESOURCE URL
    // =========================================================

    @Column(
            length = 500
    )
    private String resourceUrl;


    // =========================================================
    // VIDEO FILE PATH
    // =========================================================

    /**
     * Server-side storage path of the uploaded video.
     *
     * Example:
     *
     * uploads/training-videos/550e8400-e29b-41d4-a716-446655440000.mp4
     *
     * This path must NEVER be exposed directly to the employee.
     *
     * The employee will access the video through:
     *
     * /employee/training/module/{itemId}/video
     *
     * The backend will first verify that the training item
     * belongs to the logged-in employee.
     */
    @Column(
            name = "video_path",
            length = 1000
    )
    private String videoPath;


    // =========================================================
    // VIDEO FILE NAME
    // =========================================================

    /**
     * Original filename uploaded by the trainer.
     *
     * Example:
     *
     * database-basics.mp4
     */
    @Column(
            name = "video_file_name",
            length = 255
    )
    private String videoFileName;


    // =========================================================
    // VIDEO CONTENT TYPE
    // =========================================================

    /**
     * MIME type of the uploaded video.
     *
     * Supported types will be validated by
     * VideoStorageService.
     *
     * Examples:
     *
     * video/mp4
     * video/webm
     * video/ogg
     */
    @Column(
            name = "video_content_type",
            length = 100
    )
    private String videoContentType;


    // =========================================================
    // VIDEO FILE SIZE
    // =========================================================

    /**
     * Uploaded video size in bytes.
     */
    @Column(
            name = "video_file_size"
    )
    private Long videoFileSize;


    // =========================================================
    // VIDEO UPLOADED AT
    // =========================================================

    /**
     * Date and time when the trainer uploaded the video.
     */
    @Column(
            name = "video_uploaded_at"
    )
    private LocalDateTime videoUploadedAt;


    // =========================================================
    // ESTIMATED MINUTES
    // =========================================================

    @Column(
            nullable = false
    )
    @Builder.Default
    private Integer estimatedMinutes = 30;


    // =========================================================
    // ACTIVE
    // =========================================================

    @Column(
            nullable = false
    )
    @Builder.Default
    private Boolean active = true;


    // =========================================================
    // PERSONALIZED
    // =========================================================

    @Column(
            nullable = false
    )
    @Builder.Default
    private Boolean personalized = false;


    // =========================================================
    // CREATED AT
    // =========================================================

    @Column(
            nullable = false
    )
    @Builder.Default
    private LocalDateTime createdAt =
            LocalDateTime.now();


    // =========================================================
    // UPDATED AT
    // =========================================================

    private LocalDateTime updatedAt;


    // =========================================================
    // PRE-PERSIST
    // =========================================================

    @PrePersist
    protected void onCreate() {

        LocalDateTime now = LocalDateTime.now();

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }

        if (active == null) {
            active = true;
        }

        if (personalized == null) {
            personalized = false;
        }

        if (estimatedMinutes == null
                || estimatedMinutes <= 0) {

            estimatedMinutes = 30;
        }

        if (difficultyLevel == null) {
            difficultyLevel =
                    DifficultyLevel.BEGINNER;
        }

        if (moduleType == null) {
            moduleType =
                    ModuleType.ARTICLE;
        }
    }


    // =========================================================
    // PRE-UPDATE
    // =========================================================

    @PreUpdate
    protected void onUpdate() {

        updatedAt =
                LocalDateTime.now();
    }


    // =========================================================
    // VIDEO CHECK
    // =========================================================

    /**
     * Returns true when a video is actually attached
     * to this training module.
     */
    @Transient
    public boolean hasVideo() {

        return videoPath != null
                && !videoPath.isBlank();
    }


    // =========================================================
    // ENUMS
    // =========================================================

    public enum DifficultyLevel {

        BEGINNER,

        INTERMEDIATE,

        ADVANCED
    }


    public enum ModuleType {

        VIDEO,

        ARTICLE,

        COURSE,

        DOCUMENT,

        PRACTICE
    }
}