package com.capacitix.service;

import com.capacitix.entity.ResourceType;
import com.capacitix.entity.TrainingVideo;
import com.capacitix.entity.User;
import com.capacitix.repository.TrainingVideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TrainingVideoService {

    private final TrainingVideoRepository trainingVideoRepository;

    /*
     * =========================================================
     * COMMON LEARNING RESOURCE UPLOAD DIRECTORY
     * =========================================================
     *
     * All supported resources are stored here:
     *
     * VIDEO -> mp4, webm, mov, avi, mkv
     * IMAGE -> jpg, jpeg, png, gif, webp
     * PDF   -> pdf
     * PPT   -> ppt, pptx
     * WORD  -> doc, docx
     */
    private final Path uploadDirectory =
            Paths.get("uploads/learning-resources")
                    .toAbsolutePath()
                    .normalize();

    /**
     * =========================================================
     * UPLOAD LEARNING RESOURCE
     * =========================================================
     *
     * Supports:
     *
     * VIDEO:
     * mp4, webm, mov, avi, mkv
     *
     * IMAGE:
     * jpg, jpeg, png, gif, webp
     *
     * PDF:
     * pdf
     *
     * PPT:
     * ppt, pptx
     *
     * WORD:
     * doc, docx
     *
     * Resource type can be explicitly selected or automatically
     * detected from the file extension.
     */
    public TrainingVideo uploadResource(
            String title,
            String description,
            String resourceType,
            MultipartFile file,
            User trainer
    ) throws IOException {

        // -----------------------------------------------------
        // BASIC VALIDATION
        // -----------------------------------------------------

        if (file == null || file.isEmpty()) {

            throw new IllegalArgumentException(
                    "Please select a learning resource file."
            );
        }

        if (title == null || title.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Resource title is required."
            );
        }

        if (trainer == null) {

            throw new IllegalArgumentException(
                    "Trainer information is required."
            );
        }

        // -----------------------------------------------------
        // ORIGINAL FILE NAME
        // -----------------------------------------------------

        String originalFileName =
                file.getOriginalFilename();

        if (originalFileName == null
                || originalFileName.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Invalid resource file name."
            );
        }

        /*
         * Remove any path information from the uploaded
         * filename.
         *
         * Example:
         *
         * C:\Users\Prashant\Desktop\java.pdf
         *
         * becomes:
         *
         * java.pdf
         */
        originalFileName =
                Paths.get(originalFileName)
                        .getFileName()
                        .toString();

        // -----------------------------------------------------
        // FILE EXTENSION
        // -----------------------------------------------------

        String extension =
                getExtension(originalFileName);

        // -----------------------------------------------------
        // RESOURCE TYPE
        // -----------------------------------------------------

        ResourceType type =
                resolveResourceType(
                        resourceType,
                        extension
                );

        // -----------------------------------------------------
        // EXTENSION VALIDATION
        // -----------------------------------------------------

        if (!isExtensionAllowed(type, extension)) {

            throw new IllegalArgumentException(
                    "Invalid file type for "
                            + type.name()
                            .toLowerCase(Locale.ROOT)
                            + " resource. Selected file: "
                            + originalFileName
            );
        }

        // -----------------------------------------------------
        // CONTENT TYPE / MIME TYPE
        // -----------------------------------------------------

        String contentType =
                file.getContentType();

        if (contentType == null
                || contentType.isBlank()) {

            contentType =
                    detectContentType(extension);
        }

        /*
         * Browsers sometimes send:
         *
         * application/octet-stream
         *
         * for unknown files.
         *
         * Replace it with our known MIME type when possible.
         */
        if ("application/octet-stream"
                .equalsIgnoreCase(contentType)
                || "binary/octet-stream"
                .equalsIgnoreCase(contentType)) {

            contentType =
                    detectContentType(extension);
        }

        // -----------------------------------------------------
        // CREATE UPLOAD DIRECTORY
        // -----------------------------------------------------

        Files.createDirectories(
                uploadDirectory
        );

        // -----------------------------------------------------
        // CREATE UNIQUE STORED FILE NAME
        // -----------------------------------------------------

        String storedFileName =
                UUID.randomUUID()
                        + extension;

        Path targetPath =
                uploadDirectory
                        .resolve(storedFileName)
                        .normalize();

        // -----------------------------------------------------
        // PATH SECURITY CHECK
        // -----------------------------------------------------

        if (!targetPath.startsWith(uploadDirectory)) {

            throw new IllegalArgumentException(
                    "Invalid file path."
            );
        }

        // -----------------------------------------------------
        // SAVE PHYSICAL FILE
        // -----------------------------------------------------

        try {

            Files.copy(
                    file.getInputStream(),
                    targetPath,
                    StandardCopyOption.REPLACE_EXISTING
            );

        } catch (IOException e) {

            throw new IOException(
                    "Unable to save learning resource file.",
                    e
            );
        }

        // -----------------------------------------------------
        // CREATE DATABASE ENTITY
        // -----------------------------------------------------

        TrainingVideo resource =
                TrainingVideo.builder()
                        .title(title.trim())
                        .description(
                                description == null
                                        ? ""
                                        : description.trim()
                        )
                        .resourceType(type)
                        .originalFileName(
                                originalFileName
                        )
                        .storedFileName(
                                storedFileName
                        )
                        .filePath(
                                targetPath.toString()
                        )
                        .contentType(
                                contentType
                        )
                        .fileSize(
                                file.getSize()
                        )
                        .trainer(
                                trainer
                        )
                        .active(true)
                        .build();

        // -----------------------------------------------------
        // SAVE DATABASE RECORD
        // -----------------------------------------------------

        try {

            return trainingVideoRepository.save(
                    resource
            );

        } catch (RuntimeException e) {

            /*
             * If database save fails, remove the physical file
             * to avoid orphan files.
             */
            try {

                Files.deleteIfExists(
                        targetPath
                );

            } catch (IOException ignored) {
                // Do not hide original database exception.
            }

            throw e;
        }
    }

    // =========================================================
    // BACKWARD COMPATIBILITY - VIDEO UPLOAD
    // =========================================================

    /**
     * Existing video upload method.
     *
     * Existing code can continue using:
     *
     * uploadVideo(
     *     title,
     *     description,
     *     file,
     *     trainer
     * )
     */
    public TrainingVideo uploadVideo(
            String title,
            String description,
            MultipartFile file,
            User trainer
    ) throws IOException {

        return uploadResource(
                title,
                description,
                "VIDEO",
                file,
                trainer
        );
    }

    /**
     * Existing method that accepts resource type.
     */
    public TrainingVideo uploadVideo(
            String title,
            String description,
            String resourceType,
            MultipartFile file,
            User trainer
    ) throws IOException {

        return uploadResource(
                title,
                description,
                resourceType,
                file,
                trainer
        );
    }

    // =========================================================
    // GET TRAINER RESOURCES
    // =========================================================

    /**
     * Returns all resources uploaded by a trainer.
     *
     * Includes:
     *
     * VIDEO
     * IMAGE
     * PDF
     * PPT
     * WORD
     */
    public List<TrainingVideo> getTrainerVideos(
            User trainer
    ) {

        return trainingVideoRepository
                .findByTrainerOrderByUploadedAtDesc(
                        trainer
                );
    }

    // =========================================================
    // GET ACTIVE RESOURCES
    // =========================================================

    /**
     * Returns all active learning resources.
     */
    public List<TrainingVideo> getActiveVideos() {

        return trainingVideoRepository
                .findByActiveTrueOrderByUploadedAtDesc();
    }

    // =========================================================
    // GET RESOURCE BY ID
    // =========================================================

    /**
     * Find a learning resource by ID.
     */
    public TrainingVideo getVideoById(
            Long id
    ) {

        if (id == null) {

            throw new IllegalArgumentException(
                    "Learning resource ID is required."
            );
        }

        return trainingVideoRepository
                .findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Learning resource not found with ID: "
                                        + id
                        )
                );
    }

    // =========================================================
    // LOAD RESOURCE AS SPRING RESOURCE
    // =========================================================

    /**
     * Used for:
     *
     * /view
     * /download
     */
    public Resource loadResourceAsResource(
            TrainingVideo resource
    ) throws IOException {

        if (resource == null) {

            throw new IllegalArgumentException(
                    "Learning resource not found."
            );
        }

        Path path =
                getFilePath(resource);

        try {

            Resource fileResource =
                    new UrlResource(
                            path.toUri()
                    );

            if (!fileResource.exists()
                    || !fileResource.isReadable()) {

                throw new IllegalArgumentException(
                        "Resource file cannot be read."
                );
            }

            return fileResource;

        } catch (MalformedURLException e) {

            throw new IOException(
                    "Unable to load learning resource.",
                    e
            );
        }
    }

    // =========================================================
    // GET RESOURCE FILE PATH BY ID
    // =========================================================

    public Path getFilePath(
            Long id
    ) {

        TrainingVideo resource =
                getVideoById(id);

        return getFilePath(resource);
    }

    // =========================================================
    // GET RESOURCE FILE PATH
    // =========================================================

    private Path getFilePath(
            TrainingVideo resource
    ) {

        if (resource.getFilePath() == null
                || resource.getFilePath().isBlank()) {

            throw new IllegalArgumentException(
                    "File path is not available."
            );
        }

        Path path =
                Paths.get(
                        resource.getFilePath()
                )
                .toAbsolutePath()
                .normalize();

        /*
         * Prevent accessing files outside the configured
         * learning-resource directory.
         */
        if (!path.startsWith(uploadDirectory)) {

            throw new SecurityException(
                    "Invalid resource file path."
            );
        }

        if (!Files.exists(path)
                || !Files.isRegularFile(path)) {

            throw new IllegalArgumentException(
                    "Resource file does not exist."
            );
        }

        return path;
    }

    // =========================================================
    // DEACTIVATE RESOURCE
    // =========================================================

    /**
     * Soft delete:
     *
     * active = false
     *
     * The physical file is intentionally kept so existing
     * database references are not broken.
     */
    public void deactivateVideo(
            Long id,
            User trainer
    ) {

        TrainingVideo resource =
                getVideoById(id);

        if (resource.getTrainer() == null
                || trainer == null
                || resource.getTrainer().getId() == null
                || trainer.getId() == null
                || !resource.getTrainer()
                        .getId()
                        .equals(trainer.getId())) {

            throw new SecurityException(
                    "You are not authorized to deactivate this resource."
            );
        }

        resource.setActive(false);

        trainingVideoRepository.save(
                resource
        );
    }

    // =========================================================
    // CHECK TRAINER OWNERSHIP
    // =========================================================

    public boolean belongsToTrainer(
            TrainingVideo resource,
            User trainer
    ) {

        return resource != null
                && resource.getTrainer() != null
                && resource.getTrainer().getId() != null
                && trainer != null
                && trainer.getId() != null
                && resource.getTrainer()
                        .getId()
                        .equals(trainer.getId());
    }

    // =========================================================
    // RESOLVE RESOURCE TYPE
    // =========================================================

    /**
     * If resourceType is provided, use the selected type.
     *
     * If resourceType is empty, automatically detect it.
     */
    private ResourceType resolveResourceType(
            String resourceType,
            String extension
    ) {

        // -----------------------------------------------------
        // USER SELECTED TYPE
        // -----------------------------------------------------

        if (resourceType != null
                && !resourceType.trim().isEmpty()
                && !"AUTO".equalsIgnoreCase(
                        resourceType.trim()
                )) {

            return parseResourceType(
                    resourceType
            );
        }

        // -----------------------------------------------------
        // AUTO DETECTION
        // -----------------------------------------------------

        return switch (extension) {

            // VIDEO
            case ".mp4",
                 ".webm",
                 ".mov",
                 ".avi",
                 ".mkv" ->
                    ResourceType.VIDEO;

            // IMAGE
            case ".jpg",
                 ".jpeg",
                 ".png",
                 ".gif",
                 ".webp" ->
                    ResourceType.IMAGE;

            // PDF
            case ".pdf" ->
                    ResourceType.PDF;

            // POWERPOINT
            case ".ppt",
                 ".pptx" ->
                    ResourceType.PPT;

            // WORD
            case ".doc",
                 ".docx" ->
                    ResourceType.WORD;

            default ->
                    throw new IllegalArgumentException(
                            "Unable to detect resource type from file: "
                                    + extension
                    );
        };
    }

    // =========================================================
    // PARSE RESOURCE TYPE
    // =========================================================

    private ResourceType parseResourceType(
            String resourceType
    ) {

        if (resourceType == null
                || resourceType.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Resource type is required."
            );
        }

        try {

            return ResourceType.valueOf(
                    resourceType
                            .trim()
                            .toUpperCase(Locale.ROOT)
            );

        } catch (IllegalArgumentException e) {

            throw new IllegalArgumentException(
                    "Unsupported resource type: "
                            + resourceType
            );
        }
    }

    // =========================================================
    // FILE EXTENSION
    // =========================================================

    private String getExtension(
            String fileName
    ) {

        int lastDot =
                fileName.lastIndexOf('.');

        if (lastDot < 0
                || lastDot == fileName.length() - 1) {

            throw new IllegalArgumentException(
                    "File must have a valid extension."
            );
        }

        return fileName
                .substring(lastDot)
                .toLowerCase(Locale.ROOT);
    }

    // =========================================================
    // EXTENSION VALIDATION
    // =========================================================

    private boolean isExtensionAllowed(
            ResourceType type,
            String extension
    ) {

        if (type == null) {
            return false;
        }

        return switch (type) {

            // -------------------------------------------------
            // VIDEO
            // -------------------------------------------------

            case VIDEO ->
                    extension.equals(".mp4")
                            || extension.equals(".webm")
                            || extension.equals(".mov")
                            || extension.equals(".avi")
                            || extension.equals(".mkv");

            // -------------------------------------------------
            // IMAGE
            // -------------------------------------------------

            case IMAGE ->
                    extension.equals(".jpg")
                            || extension.equals(".jpeg")
                            || extension.equals(".png")
                            || extension.equals(".gif")
                            || extension.equals(".webp");

            // -------------------------------------------------
            // PDF
            // -------------------------------------------------

            case PDF ->
                    extension.equals(".pdf");

            // -------------------------------------------------
            // POWERPOINT
            // -------------------------------------------------

            case PPT ->
                    extension.equals(".ppt")
                            || extension.equals(".pptx");

            // -------------------------------------------------
            // WORD
            // -------------------------------------------------

            case WORD ->
                    extension.equals(".doc")
                            || extension.equals(".docx");
        };
    }

    // =========================================================
    // FALLBACK MIME TYPE
    // =========================================================

    private String detectContentType(
            String extension
    ) {

        return switch (extension) {

            // -------------------------------------------------
            // VIDEO
            // -------------------------------------------------

            case ".mp4" ->
                    "video/mp4";

            case ".webm" ->
                    "video/webm";

            case ".mov" ->
                    "video/quicktime";

            case ".avi" ->
                    "video/x-msvideo";

            case ".mkv" ->
                    "video/x-matroska";

            // -------------------------------------------------
            // IMAGE
            // -------------------------------------------------

            case ".jpg",
                 ".jpeg" ->
                    "image/jpeg";

            case ".png" ->
                    "image/png";

            case ".gif" ->
                    "image/gif";

            case ".webp" ->
                    "image/webp";

            // -------------------------------------------------
            // PDF
            // -------------------------------------------------

            case ".pdf" ->
                    "application/pdf";

            // -------------------------------------------------
            // POWERPOINT
            // -------------------------------------------------

            case ".ppt" ->
                    "application/vnd.ms-powerpoint";

            case ".pptx" ->
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation";

            // -------------------------------------------------
            // WORD
            // -------------------------------------------------

            case ".doc" ->
                    "application/msword";

            case ".docx" ->
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

            // -------------------------------------------------
            // UNKNOWN
            // -------------------------------------------------

            default ->
                    "application/octet-stream";
        };
    }
}