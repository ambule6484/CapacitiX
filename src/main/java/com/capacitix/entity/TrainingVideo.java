package com.capacitix.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "training_videos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainingVideo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Display title of the learning resource.
     */
    @Column(nullable = false, length = 150)
    private String title;

    /**
     * Optional description.
     */
    @Column(length = 1000)
    private String description;

    /**
     * Type of learning resource:
     * VIDEO, IMAGE, PDF, PPT, WORD
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 20)
    private ResourceType resourceType;

    /**
     * Original file name uploaded by the trainer.
     * Example: java-oops.pdf
     */
    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    /**
     * Actual file name used for storage.
     * Example: 8f3a21c9-java-oops.pdf
     */
    @Column(name = "stored_file_name", nullable = false, length = 255)
    private String storedFileName;

    /**
     * Location of the stored file on the server.
     */
    @Column(name = "file_path", nullable = false, length = 1000)
    private String filePath;

    /**
     * MIME type of the uploaded file.
     * Example:
     * video/mp4
     * image/png
     * application/pdf
     */
    @Column(name = "content_type", nullable = false, length = 150)
    private String contentType;

    /**
     * File size in bytes.
     */
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /**
     * Trainer who uploaded the resource.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trainer_id", nullable = false)
    private User trainer;

    /**
     * Upload date and time.
     */
    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    /**
     * Soft-delete / active flag.
     */
    @Column(nullable = false)
    private boolean active = true;

    @PrePersist
    protected void onCreate() {
        if (uploadedAt == null) {
            uploadedAt = LocalDateTime.now();
        }

        if (fileSize == null) {
            fileSize = 0L;
        }

        if (active == false) {
            // Keep explicitly false if required.
        } else {
            active = true;
        }
    }
}