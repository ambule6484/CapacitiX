
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

    // =========================================================
    // UPLOAD DIRECTORY
    // =========================================================

    private final Path uploadDirectory =
            Paths.get("uploads/learning-resources")
                    .toAbsolutePath()
                    .normalize();

    // =========================================================
    // UPLOAD LEARNING RESOURCE
    // =========================================================

    public TrainingVideo uploadResource(
            String title,
            String description,
            String resourceType,
            MultipartFile file,
            User trainer
    ) throws IOException {

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

        String originalFileName = file.getOriginalFilename();

        if (originalFileName == null
                || originalFileName.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid resource file name."
            );
        }

        originalFileName = Paths.get(originalFileName)
                .getFileName()
                .toString();

        String extension = getExtension(originalFileName);

        ResourceType type = resolveResourceType(
                resourceType,
                extension
        );

        if (!isExtensionAllowed(type, extension)) {
            throw new IllegalArgumentException(
                    "Invalid file type for "
                            + type.name().toLowerCase(Locale.ROOT)
                            + " resource. Selected file: "
                            + originalFileName
            );
        }

        String contentType = file.getContentType();

        if (contentType == null
                || contentType.isBlank()
                || "application/octet-stream".equalsIgnoreCase(contentType)
                || "binary/octet-stream".equalsIgnoreCase(contentType)) {

            contentType = detectContentType(extension);
        }

        Files.createDirectories(uploadDirectory);

        String storedFileName = UUID.randomUUID() + extension;

        Path targetPath = uploadDirectory
                .resolve(storedFileName)
                .normalize();

        if (!targetPath.startsWith(uploadDirectory)) {
            throw new IllegalArgumentException(
                    "Invalid file path."
            );
        }

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

        TrainingVideo resource = TrainingVideo.builder()
                .title(title.trim())
                .description(
                        description == null ? "" : description.trim()
                )
                .resourceType(type)
                .originalFileName(originalFileName)
                .storedFileName(storedFileName)
                .filePath(targetPath.toString())
                .contentType(contentType)
                .fileSize(file.getSize())
                .trainer(trainer)
                .active(true)
                .build();

        try {
            return trainingVideoRepository.save(resource);
        } catch (RuntimeException e) {

            try {
                Files.deleteIfExists(targetPath);
            } catch (IOException ignored) {
                // Preserve the original database exception.
            }

            throw e;
        }
    }

    // =========================================================
    // BACKWARD COMPATIBILITY - VIDEO UPLOAD
    // =========================================================

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

    public List<TrainingVideo> getTrainerVideos(User trainer) {

        return trainingVideoRepository
                .findByTrainerOrderByUploadedAtDesc(trainer);
    }

    // =========================================================
    // GET ACTIVE RESOURCES
    // =========================================================

    public List<TrainingVideo> getActiveVideos() {

        return trainingVideoRepository
                .findByActiveTrueOrderByUploadedAtDesc();
    }

    // =========================================================
    // GET RESOURCE BY ID
    // =========================================================

    public TrainingVideo getVideoById(Long id) {

        if (id == null) {
            throw new IllegalArgumentException(
                    "Learning resource ID is required."
            );
        }

        return trainingVideoRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Learning resource not found with ID: " + id
                        )
                );
    }

    // =========================================================
    // LOAD RESOURCE AS SPRING RESOURCE
    // =========================================================

    public Resource loadResourceAsResource(
            TrainingVideo resource
    ) throws IOException {

        if (resource == null) {
            throw new IllegalArgumentException(
                    "Learning resource not found."
            );
        }

        Path path = getFilePath(resource);

        Resource fileResource = new UrlResource(path.toUri());

        if (!fileResource.exists()
                || !fileResource.isReadable()) {

            throw new IOException(
                    "Learning resource file is not readable: " + path
            );
        }

        return fileResource;
    }

    // =========================================================
    // GET RESOURCE FILE PATH BY ID
    // =========================================================

    public Path getFilePath(Long id) {

        TrainingVideo resource = getVideoById(id);

        return getFilePath(resource);
    }

    // =========================================================
    // GET RESOURCE FILE PATH
    // SINGLE IMPLEMENTATION
    // =========================================================

    private Path getFilePath(TrainingVideo resource) {

        if (resource == null) {
            throw new IllegalArgumentException(
                    "Learning resource is required."
            );
        }

        String storedPath = resource.getFilePath();

        // First try the original database path.
        if (storedPath != null && !storedPath.isBlank()) {

            try {
                Path path = Paths.get(storedPath)
                        .toAbsolutePath()
                        .normalize();

                if (path.startsWith(uploadDirectory)
                        && Files.exists(path)
                        && Files.isRegularFile(path)
                        && Files.isReadable(path)) {

                    return path;
                }

            } catch (Exception ex) {
                // Try stored filename fallback.
            }
        }

        // Fallback: reconstruct using the stored filename.
        String storedFileName = resource.getStoredFileName();

        if (storedFileName != null && !storedFileName.isBlank()) {

            Path fallbackPath = uploadDirectory
                    .resolve(storedFileName)
                    .normalize();

            if (fallbackPath.startsWith(uploadDirectory)
                    && Files.exists(fallbackPath)
                    && Files.isRegularFile(fallbackPath)
                    && Files.isReadable(fallbackPath)) {

                return fallbackPath;
            }
        }

        throw new IllegalArgumentException(
                "Resource file not found. Resource ID: "
                        + resource.getId()
                        + ", Stored path: "
                        + storedPath
                        + ", Stored filename: "
                        + storedFileName
        );
    }

    // =========================================================
    // DEACTIVATE RESOURCE
    // =========================================================

    public void deactivateVideo(
            Long id,
            User trainer
    ) {

        TrainingVideo resource = getVideoById(id);

        if (resource.getTrainer() == null
                || trainer == null
                || resource.getTrainer().getId() == null
                || trainer.getId() == null
                || !resource.getTrainer().getId()
                        .equals(trainer.getId())) {

            throw new SecurityException(
                    "You are not authorized to deactivate this resource."
            );
        }

        resource.setActive(false);

        trainingVideoRepository.save(resource);
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
                && resource.getTrainer().getId()
                        .equals(trainer.getId());
    }

    // =========================================================
    // RESOLVE RESOURCE TYPE
    // =========================================================

    private ResourceType resolveResourceType(
            String resourceType,
            String extension
    ) {

        if (resourceType != null
                && !resourceType.trim().isEmpty()
                && !"AUTO".equalsIgnoreCase(resourceType.trim())) {

            return parseResourceType(resourceType);
        }

        return switch (extension) {

            case ".mp4", ".webm", ".mov", ".avi", ".mkv" ->
                    ResourceType.VIDEO;

            case ".jpg", ".jpeg", ".png", ".gif", ".webp" ->
                    ResourceType.IMAGE;

            case ".pdf" ->
                    ResourceType.PDF;

            case ".ppt", ".pptx" ->
                    ResourceType.PPT;

            case ".doc", ".docx" ->
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

    private ResourceType parseResourceType(String resourceType) {

        if (resourceType == null || resourceType.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Resource type is required."
            );
        }

        try {
            return ResourceType.valueOf(
                    resourceType.trim().toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unsupported resource type: " + resourceType
            );
        }
    }

    // =========================================================
    // FILE EXTENSION
    // =========================================================

    private String getExtension(String fileName) {

        int lastDot = fileName.lastIndexOf('.');

        if (lastDot < 0 || lastDot == fileName.length() - 1) {
            throw new IllegalArgumentException(
                    "File must have a valid extension."
            );
        }

        return fileName.substring(lastDot).toLowerCase(Locale.ROOT);
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

            case VIDEO ->
                    List.of(
                            ".mp4", ".webm", ".mov", ".avi", ".mkv"
                    ).contains(extension);

            case IMAGE ->
                    List.of(
                            ".jpg", ".jpeg", ".png", ".gif", ".webp"
                    ).contains(extension);

            case PDF ->
                    extension.equals(".pdf");

            case PPT ->
                    List.of(".ppt", ".pptx").contains(extension);

            case WORD ->
                    List.of(".doc", ".docx").contains(extension);
        };
    }

    // =========================================================
    // DETECT MIME TYPE
    // =========================================================

    private String detectContentType(String extension) {

        return switch (extension) {

            // VIDEO
            case ".mp4" -> "video/mp4";
            case ".webm" -> "video/webm";
            case ".mov" -> "video/quicktime";
            case ".avi" -> "video/x-msvideo";
            case ".mkv" -> "video/x-matroska";

            // IMAGE
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".png" -> "image/png";
            case ".gif" -> "image/gif";
            case ".webp" -> "image/webp";

            // PDF
            case ".pdf" -> "application/pdf";

            // POWERPOINT
            case ".ppt" -> "application/vnd.ms-powerpoint";
            case ".pptx" ->
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation";

            // WORD
            case ".doc" -> "application/msword";
            case ".docx" ->
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

            default -> "application/octet-stream";
        };
    }
}
