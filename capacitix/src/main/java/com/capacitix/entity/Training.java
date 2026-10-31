package com.capacitix.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "trainings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Training {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String category = "Technical";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trainer_id")
    private User trainer;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "DRAFT";

    @Column(nullable = false)
    @Builder.Default
    private Integer assignedEmployees = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer progress = 0;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }

        if (status == null || status.trim().isEmpty()) {
            status = "DRAFT";
        }

        if (category == null || category.trim().isEmpty()) {
            category = "Technical";
        }

        if (assignedEmployees == null || assignedEmployees < 0) {
            assignedEmployees = 0;
        }

        if (progress == null || progress < 0) {
            progress = 0;
        }

        if (progress > 100) {
            progress = 100;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();

        if (assignedEmployees == null || assignedEmployees < 0) {
            assignedEmployees = 0;
        }

        if (progress == null || progress < 0) {
            progress = 0;
        }

        if (progress > 100) {
            progress = 100;
        }
    }

    @Transient
    public String getTrainerName() {
        if (trainer == null) {
            return "Not Assigned";
        }

        if (trainer.getFullName() == null ||
                trainer.getFullName().trim().isEmpty()) {
            return "Not Assigned";
        }

        return trainer.getFullName();
    }

    @Transient
    public String getStatusClass() {

        if (status == null) {
            return "status-draft";
        }

        return switch (status.toUpperCase()) {
            case "ACTIVE" -> "status-active";
            case "COMPLETED" -> "status-completed";
            default -> "status-draft";
        };
    }
}