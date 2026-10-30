package com.capacitix.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "employee_video_assignments",
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {"video_id", "employee_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeVideoAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Learning resource assigned to the employee.
     *
     * The field name remains "video" so existing
     * service/controller code does not need unnecessary
     * renaming. It can now represent:
     * VIDEO, IMAGE, PDF, PPT or WORD.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id", nullable = false)
    private TrainingVideo video;

    /**
     * Employee who receives the learning resource.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private User employee;

    /**
     * Trainer who assigned the learning resource.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trainer_id", nullable = false)
    private User trainer;

    /**
     * Date and time when the resource was assigned.
     */
    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    /**
     * Assignment status.
     *
     * ASSIGNED
     * IN_PROGRESS
     * COMPLETED
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VideoAssignmentStatus status;

    /**
     * Completion percentage.
     *
     * For videos this can represent playback progress.
     * For other resources it can represent learning progress.
     */
    @Column(name = "progress_percentage", nullable = false)
    private Integer progressPercentage = 0;

    /**
     * Date and time when the employee completed the resource.
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {

        if (assignedAt == null) {
            assignedAt = LocalDateTime.now();
        }

        if (status == null) {
            status = VideoAssignmentStatus.ASSIGNED;
        }

        if (progressPercentage == null) {
            progressPercentage = 0;
        }
    }
}