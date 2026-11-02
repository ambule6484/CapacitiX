
package com.capacitix.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "practical_task_submissions",
        indexes = {
                @Index(
                        name = "idx_task_submission_task",
                        columnList = "practical_task_id"
                ),
                @Index(
                        name = "idx_task_submission_employee",
                        columnList = "employee_id"
                ),
                @Index(
                        name = "idx_task_submission_status",
                        columnList = "status"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {
        "practicalTask",
        "employee"
})
public class PracticalTaskSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // ============================================================
    // RELATIONSHIPS
    // ============================================================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "practical_task_id",
            nullable = false
    )
    private PracticalTask practicalTask;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "employee_id",
            nullable = false
    )
    private User employee;


    // ============================================================
    // EMPLOYEE SUBMISSION DATA
    // ============================================================

    @Column(columnDefinition = "LONGTEXT")
    private String solutionDescription;

    @Column(columnDefinition = "LONGTEXT")
    private String submittedAnswer;

    @Column(
            name = "submission_url",
            length = 1000
    )
    private String submissionUrl;

    @Column(
            name = "attachment_path",
            length = 1000
    )
    private String attachmentPath;

    @Column(
            name = "attachment_file_name",
            length = 255
    )
    private String attachmentFileName;

    @Column(
            name = "attachment_content_type",
            length = 150
    )
    private String attachmentContentType;


    // ============================================================
    // SUBMISSION STATUS
    // ============================================================

    @Column(nullable = false)
    @Builder.Default
    private Integer progressPercentage = 0;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 30
    )
    @Builder.Default
    private SubmissionStatus status =
            SubmissionStatus.NOT_STARTED;


    // ============================================================
    // TRAINER REVIEW
    // ============================================================

    @Column(
            name = "trainer_feedback",
            columnDefinition = "TEXT"
    )
    private String trainerFeedback;

    @Column(nullable = false)
    @Builder.Default
    private Boolean reviewed = false;

    @Column
    private Integer score;


    // ============================================================
    // TIMESTAMPS
    // ============================================================

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;


    // ============================================================
    // JPA LIFECYCLE
    // ============================================================

    @PrePersist
    protected void onCreate() {

        LocalDateTime now = LocalDateTime.now();

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }

        if (progressPercentage == null) {
            progressPercentage = 0;
        }

        if (status == null) {
            status = SubmissionStatus.NOT_STARTED;
        }

        if (reviewed == null) {
            reviewed = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }


    // ============================================================
    // EMPLOYEE ACTIONS
    // ============================================================

    public void start() {

        if (status == SubmissionStatus.APPROVED) {
            return;
        }

        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }

        status = SubmissionStatus.IN_PROGRESS;

        if (progressPercentage == null || progressPercentage <= 0) {
            progressPercentage = 1;
        }
    }


    public void updateProgress(Integer progress) {

        if (progress == null ||
                status == SubmissionStatus.APPROVED) {
            return;
        }

        int safeProgress = Math.max(
                0,
                Math.min(99, progress)
        );

        progressPercentage = safeProgress;

        if (safeProgress > 0) {

            if (startedAt == null) {
                startedAt = LocalDateTime.now();
            }

            status = SubmissionStatus.IN_PROGRESS;
        } else {
            status = SubmissionStatus.NOT_STARTED;
        }
    }


    public void submit() {

        if (status == SubmissionStatus.APPROVED) {
            return;
        }

        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }

        status = SubmissionStatus.SUBMITTED;

        progressPercentage = 99;

        submittedAt = LocalDateTime.now();

        reviewed = false;
        reviewedAt = null;
        score = null;
    }


    // ============================================================
    // TRAINER ACTIONS
    // ============================================================

    public void approve(
            Integer score,
            String feedback
    ) {

        if (status != SubmissionStatus.SUBMITTED) {
            throw new IllegalStateException(
                    "Only submitted work can be approved."
            );
        }

        if (score != null) {
            this.score = Math.max(
                    0,
                    Math.min(100, score)
            );
        } else {
            this.score = null;
        }

        this.trainerFeedback = feedback;

        this.reviewed = true;

        this.reviewedAt = LocalDateTime.now();

        this.status = SubmissionStatus.APPROVED;

        this.progressPercentage = 100;
    }


    public void requestImprovement(String feedback) {

        if (status != SubmissionStatus.SUBMITTED) {
            throw new IllegalStateException(
                    "Only submitted work can be sent for improvement."
            );
        }

        this.trainerFeedback = feedback;

        this.reviewed = true;

        this.reviewedAt = LocalDateTime.now();

        this.status = SubmissionStatus.NEEDS_IMPROVEMENT;

        this.progressPercentage = 99;
    }


    public void prepareForResubmission() {

        if (status != SubmissionStatus.NEEDS_IMPROVEMENT) {
            throw new IllegalStateException(
                    "Submission is not waiting for improvement."
            );
        }

        this.reviewed = false;
        this.reviewedAt = null;
        this.score = null;
        this.submittedAt = null;

        this.status = SubmissionStatus.IN_PROGRESS;

        this.progressPercentage = 1;
    }


    // ============================================================
    // STATUS HELPERS
    // ============================================================

    public boolean isSubmitted() {
        return status == SubmissionStatus.SUBMITTED;
    }

    public boolean isApproved() {
        return status == SubmissionStatus.APPROVED;
    }

    public boolean needsImprovement() {
        return status == SubmissionStatus.NEEDS_IMPROVEMENT;
    }

    public boolean isPendingReview() {
        return status == SubmissionStatus.SUBMITTED;
    }


    // ============================================================
    // ENUM
    // ============================================================

    public enum SubmissionStatus {

        NOT_STARTED,

        IN_PROGRESS,

        SUBMITTED,

        NEEDS_IMPROVEMENT,

        APPROVED
    }
}
