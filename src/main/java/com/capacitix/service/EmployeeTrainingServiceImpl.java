package com.capacitix.service;

import com.capacitix.entity.EmployeeTrainingProgress;
import com.capacitix.entity.LearningPath;
import com.capacitix.entity.LearningPathItem;
import com.capacitix.entity.User;
import com.capacitix.repository.EmployeeTrainingProgressRepository;
import com.capacitix.repository.LearningPathItemRepository;
import com.capacitix.repository.LearningPathRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class EmployeeTrainingServiceImpl
        implements EmployeeTrainingService {

    /*
     * Progress given when an employee opens a module
     * for the first time.
     *
     * Opening a module means learning has started.
     * It does NOT mean the module is completed.
     */
    private static final int INITIAL_ACCESS_PROGRESS = 5;

    private final LearningPathRepository learningPathRepository;

    private final LearningPathItemRepository learningPathItemRepository;

    private final EmployeeTrainingProgressRepository
            employeeTrainingProgressRepository;


    // =========================================================
    // SAVED LEARNING PATH
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public Optional<LearningPath> getActiveLearningPath(
            User employee
    ) {

        if (employee == null) {
            return Optional.empty();
        }

        return learningPathRepository
                .findTopByEmployeeAndStatusOrderByCreatedAtDesc(
                        employee,
                        LearningPath.LearningPathStatus.ACTIVE
                );
    }


    @Override
    @Transactional(readOnly = true)
    public Optional<LearningPath> getLatestLearningPath(
            User employee
    ) {

        if (employee == null) {
            return Optional.empty();
        }

        return learningPathRepository
                .findTopByEmployeeOrderByCreatedAtDesc(
                        employee
                );
    }


    // =========================================================
    // EMPLOYEE TRAINING ITEMS
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<LearningPathItem> getEmployeeTrainingItems(
            User employee
    ) {

        if (employee == null) {
            return Collections.emptyList();
        }

        Optional<LearningPath> learningPath =
                getActiveLearningPath(employee);

        if (learningPath.isEmpty()) {
            learningPath =
                    getLatestLearningPath(employee);
        }

        if (learningPath.isEmpty()) {
            return Collections.emptyList();
        }

        return learningPathItemRepository
                .findByLearningPathOrderBySequenceOrderAsc(
                        learningPath.get()
                );
    }


    // =========================================================
    // EMPLOYEE PROGRESS
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeTrainingProgress> getEmployeeProgress(
            User employee
    ) {

        if (employee == null) {
            return Collections.emptyList();
        }

        return employeeTrainingProgressRepository
                .findByEmployee(employee);
    }


    @Override
    @Transactional(readOnly = true)
    public Optional<EmployeeTrainingProgress> getProgress(
            User employee,
            LearningPathItem learningPathItem
    ) {

        if (employee == null
                || learningPathItem == null) {

            return Optional.empty();
        }

        if (!belongsToEmployee(
                employee,
                learningPathItem
        )) {

            return Optional.empty();
        }

        return employeeTrainingProgressRepository
                .findByEmployeeAndLearningPathItem(
                        employee,
                        learningPathItem
                );
    }


    // =========================================================
    // START / ACCESS TRAINING
    // =========================================================

    @Override
    public EmployeeTrainingProgress startTraining(
            User employee,
            LearningPathItem learningPathItem
    ) {

        validateEmployeeAndItem(
                employee,
                learningPathItem
        );

        EmployeeTrainingProgress progress =
                getOrCreateProgress(
                        employee,
                        learningPathItem
                );

        LocalDateTime now =
                LocalDateTime.now();


        // =====================================================
        // MARK MODULE AS STARTED
        // =====================================================

        if (!Boolean.TRUE.equals(
                progress.getStarted()
        )) {

            progress.setStarted(true);

            if (progress.getStartedAt() == null) {

                progress.setStartedAt(
                        now
                );
            }
        }


        // =====================================================
        // RECORD LAST ACCESS
        // =====================================================

        progress.setLastAccessedAt(
                now
        );


        // =====================================================
        // GET CURRENT PROGRESS
        // =====================================================

        Integer currentProgress =
                progress.getProgressPercentage();

        if (currentProgress == null) {
            currentProgress = 0;
        }

        currentProgress =
                clampPercentage(
                        currentProgress
                );


        // =====================================================
        // FIRST ACCESS = 5%
        // =====================================================

        if (currentProgress < INITIAL_ACCESS_PROGRESS) {

            progress.setProgressPercentage(
                    INITIAL_ACCESS_PROGRESS
            );

            currentProgress =
                    INITIAL_ACCESS_PROGRESS;

        } else {

            progress.setProgressPercentage(
                    currentProgress
            );
        }


        // =====================================================
        // UPDATE STATUS
        // =====================================================

        if (currentProgress >= 100) {

            progress.setProgressPercentage(100);

            progress.setStatus(
                    EmployeeTrainingProgress.TrainingStatus
                            .COMPLETED
            );

            if (progress.getCompletedAt() == null) {

                progress.setCompletedAt(
                        now
                );
            }

        } else {

            progress.setStatus(
                    EmployeeTrainingProgress.TrainingStatus
                            .IN_PROGRESS
            );

            progress.setCompletedAt(null);
        }


        // =====================================================
        // SAVE
        // =====================================================

        EmployeeTrainingProgress saved =
                employeeTrainingProgressRepository.save(
                        progress
                );


        // =====================================================
        // UPDATE LEARNING PATH AGGREGATE
        // =====================================================

        updateLearningPathProgress(
                learningPathItem.getLearningPath()
        );


        return saved;
    }


    // =========================================================
    // UPDATE PROGRESS
    // =========================================================

    @Override
    public EmployeeTrainingProgress updateProgress(
            User employee,
            LearningPathItem learningPathItem,
            Integer progressPercentage
    ) {

        validateEmployeeAndItem(
                employee,
                learningPathItem
        );

        EmployeeTrainingProgress progress =
                getOrCreateProgress(
                        employee,
                        learningPathItem
                );

        int safeProgress =
                progressPercentage == null
                        ? 0
                        : Math.max(
                                0,
                                Math.min(
                                        100,
                                        progressPercentage
                                )
                        );

        LocalDateTime now =
                LocalDateTime.now();


        // =====================================================
        // SAVE ACTUAL EMPLOYEE PROGRESS
        // =====================================================

        progress.setProgressPercentage(
                safeProgress
        );

        progress.setLastAccessedAt(
                now
        );


        // =====================================================
        // MARK STARTED
        // =====================================================

        if (safeProgress > 0) {

            progress.setStarted(true);

            if (progress.getStartedAt() == null) {

                progress.setStartedAt(
                        now
                );
            }
        }


        // =====================================================
        // UPDATE STATUS
        // =====================================================

        if (safeProgress >= 100) {

            progress.setProgressPercentage(100);

            progress.setStatus(
                    EmployeeTrainingProgress.TrainingStatus
                            .COMPLETED
            );

            progress.setStarted(true);

            if (progress.getStartedAt() == null) {

                progress.setStartedAt(
                        now
                );
            }

            if (progress.getCompletedAt() == null) {

                progress.setCompletedAt(
                        now
                );
            }

        } else if (safeProgress > 0) {

            progress.setStatus(
                    EmployeeTrainingProgress.TrainingStatus
                            .IN_PROGRESS
            );

            progress.setCompletedAt(null);

        } else {

            progress.setStatus(
                    EmployeeTrainingProgress.TrainingStatus
                            .NOT_STARTED
            );

            progress.setCompletedAt(null);
        }


        // =====================================================
        // SAVE
        // =====================================================

        EmployeeTrainingProgress saved =
                employeeTrainingProgressRepository.save(
                        progress
                );


        // =====================================================
        // UPDATE LEARNING PATH TOTAL
        // =====================================================

        updateLearningPathProgress(
                learningPathItem.getLearningPath()
        );


        return saved;
    }


    // =========================================================
    // TIME SPENT
    // =========================================================

    @Override
    public EmployeeTrainingProgress addTimeSpent(
            User employee,
            LearningPathItem learningPathItem,
            Integer minutes
    ) {

        validateEmployeeAndItem(
                employee,
                learningPathItem
        );

        EmployeeTrainingProgress progress =
                getOrCreateProgress(
                        employee,
                        learningPathItem
                );

        int safeMinutes =
                minutes == null
                        ? 0
                        : Math.max(
                                0,
                                minutes
                        );

        int currentMinutes =
                progress.getTimeSpentMinutes() == null
                        ? 0
                        : progress.getTimeSpentMinutes();

        progress.setTimeSpentMinutes(
                currentMinutes + safeMinutes
        );

        progress.setLastAccessedAt(
                LocalDateTime.now()
        );

        return employeeTrainingProgressRepository.save(
                progress
        );
    }


    // =========================================================
    // COMPLETE TRAINING
    // =========================================================

    @Override
    public EmployeeTrainingProgress completeTraining(
            User employee,
            LearningPathItem learningPathItem
    ) {

        validateEmployeeAndItem(
                employee,
                learningPathItem
        );

        EmployeeTrainingProgress progress =
                getOrCreateProgress(
                        employee,
                        learningPathItem
                );

        LocalDateTime now =
                LocalDateTime.now();

        progress.setProgressPercentage(100);

        progress.setStatus(
                EmployeeTrainingProgress.TrainingStatus
                        .COMPLETED
        );

        progress.setStarted(true);

        if (progress.getStartedAt() == null) {

            progress.setStartedAt(
                    now
            );
        }

        progress.setCompletedAt(
                now
        );

        progress.setLastAccessedAt(
                now
        );

        EmployeeTrainingProgress saved =
                employeeTrainingProgressRepository.save(
                        progress
                );

        updateLearningPathProgress(
                learningPathItem.getLearningPath()
        );

        return saved;
    }


    // =========================================================
    // ASSESSMENT SCORE
    // =========================================================

    @Override
    public EmployeeTrainingProgress updateAssessmentScore(
            User employee,
            LearningPathItem learningPathItem,
            Double score
    ) {

        validateEmployeeAndItem(
                employee,
                learningPathItem
        );

        EmployeeTrainingProgress progress =
                getOrCreateProgress(
                        employee,
                        learningPathItem
                );

        double safeScore =
                score == null
                        ? 0.0
                        : Math.max(
                                0.0,
                                Math.min(
                                        100.0,
                                        score
                                )
                        );

        progress.setAssessmentScore(
                safeScore
        );

        progress.setLastAccessedAt(
                LocalDateTime.now()
        );

        return employeeTrainingProgressRepository.save(
                progress
        );
    }


    // =========================================================
    // FEEDBACK
    // =========================================================

    @Override
    public EmployeeTrainingProgress updateFeedback(
            User employee,
            LearningPathItem learningPathItem,
            String feedback
    ) {

        validateEmployeeAndItem(
                employee,
                learningPathItem
        );

        EmployeeTrainingProgress progress =
                getOrCreateProgress(
                        employee,
                        learningPathItem
                );

        progress.setFeedback(
                feedback == null
                        ? null
                        : feedback.trim()
        );

        progress.setLastAccessedAt(
                LocalDateTime.now()
        );

        return employeeTrainingProgressRepository.save(
                progress
        );
    }


    // =========================================================
    // OVERALL LEARNING PROGRESS
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public int getOverallProgress(
            User employee
    ) {

        if (employee == null) {
            return 0;
        }

        Optional<LearningPath> learningPathOptional =
                getActiveLearningPath(employee);

        if (learningPathOptional.isEmpty()) {

            learningPathOptional =
                    getLatestLearningPath(employee);
        }

        if (learningPathOptional.isEmpty()) {
            return 0;
        }

        LearningPath learningPath =
                learningPathOptional.get();

        List<LearningPathItem> items =
                learningPathItemRepository
                        .findByLearningPathOrderBySequenceOrderAsc(
                                learningPath
                        );

        if (items == null || items.isEmpty()) {
            return 0;
        }

        List<EmployeeTrainingProgress> progressRecords =
                employeeTrainingProgressRepository
                        .findByEmployeeAndLearningPathItemIn(
                                employee,
                                items
                        );

        if (progressRecords == null) {

            progressRecords =
                    Collections.emptyList();
        }

        Map<Long, Integer> progressMap =
                new HashMap<>();

        for (EmployeeTrainingProgress progress :
                progressRecords) {

            if (progress == null) {
                continue;
            }

            LearningPathItem progressItem =
                    progress.getLearningPathItem();

            if (progressItem == null
                    || progressItem.getId() == null) {

                continue;
            }

            Integer percentage =
                    progress.getProgressPercentage();

            if (percentage == null) {
                percentage = 0;
            }

            percentage =
                    clampPercentage(
                            percentage
                    );

            Long itemId =
                    progressItem.getId();

            Integer existing =
                    progressMap.get(itemId);

            if (existing == null
                    || percentage > existing) {

                progressMap.put(
                        itemId,
                        percentage
                );
            }
        }


        int totalProgress = 0;

        int validItems = 0;

        for (LearningPathItem item : items) {

            if (item == null
                    || item.getId() == null) {

                continue;
            }

            int percentage =
                    progressMap.getOrDefault(
                            item.getId(),
                            0
                    );

            totalProgress += percentage;

            validItems++;
        }

        if (validItems == 0) {
            return 0;
        }

        return clampPercentage(
                Math.round(
                        (float) totalProgress
                                / validItems
                )
        );
    }


    // =========================================================
    // COMPLETED MODULE COUNT
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public int getCompletedModuleCount(
            User employee
    ) {

        if (employee == null) {
            return 0;
        }

        /*
         * Get the same learning path used by the
         * employee training dashboard.
         */
        Optional<LearningPath> learningPathOptional =
                getActiveLearningPath(employee);

        if (learningPathOptional.isEmpty()) {

            learningPathOptional =
                    getLatestLearningPath(employee);
        }

        if (learningPathOptional.isEmpty()) {
            return 0;
        }

        LearningPath learningPath =
                learningPathOptional.get();


        // =====================================================
        // GET CURRENT LEARNING PATH ITEMS
        // =====================================================

        List<LearningPathItem> items =
                learningPathItemRepository
                        .findByLearningPathOrderBySequenceOrderAsc(
                                learningPath
                        );

        if (items == null || items.isEmpty()) {
            return 0;
        }


        // =====================================================
        // GET EMPLOYEE PROGRESS
        // =====================================================

        List<EmployeeTrainingProgress> progressRecords =
                employeeTrainingProgressRepository
                        .findByEmployeeAndLearningPathItemIn(
                                employee,
                                items
                        );

        if (progressRecords == null
                || progressRecords.isEmpty()) {

            return 0;
        }


        // =====================================================
        // MAP PROGRESS BY ITEM
        // =====================================================

        Map<Long, EmployeeTrainingProgress> progressMap =
                new HashMap<>();

        for (EmployeeTrainingProgress progress :
                progressRecords) {

            if (progress == null
                    || progress.getLearningPathItem() == null
                    || progress.getLearningPathItem().getId() == null) {

                continue;
            }

            Long itemId =
                    progress.getLearningPathItem().getId();

            EmployeeTrainingProgress existing =
                    progressMap.get(itemId);

            /*
             * If duplicate records somehow exist,
             * keep the strongest/completed record.
             */
            if (existing == null) {

                progressMap.put(
                        itemId,
                        progress
                );

            } else {

                boolean currentCompleted =
                        isProgressCompleted(progress);

                boolean existingCompleted =
                        isProgressCompleted(existing);

                if (currentCompleted
                        && !existingCompleted) {

                    progressMap.put(
                            itemId,
                            progress
                    );

                } else if (!existingCompleted
                        && !currentCompleted) {

                    int currentPercentage =
                            getSafeProgress(
                                    progress
                            );

                    int existingPercentage =
                            getSafeProgress(
                                    existing
                            );

                    if (currentPercentage
                            > existingPercentage) {

                        progressMap.put(
                                itemId,
                                progress
                        );
                    }
                }
            }
        }


        // =====================================================
        // COUNT COMPLETED MODULES
        // =====================================================

        int completed = 0;

        for (LearningPathItem item : items) {

            if (item == null
                    || item.getId() == null) {

                continue;
            }

            EmployeeTrainingProgress progress =
                    progressMap.get(
                            item.getId()
                    );

            if (progress == null) {
                continue;
            }

            /*
             * A module is considered completed when:
             *
             * 1. Status is COMPLETED
             *
             * OR
             *
             * 2. Progress reaches 100%.
             *
             * This protects the dashboard if one field
             * was updated but the other was not.
             */
            if (isProgressCompleted(progress)) {

                completed++;
            }
        }

        return completed;
    }


    // =========================================================
    // CHECK WHETHER TRAINING IS COMPLETED
    // =========================================================

    private boolean isProgressCompleted(
            EmployeeTrainingProgress progress
    ) {

        if (progress == null) {
            return false;
        }

        /*
         * Primary completion state.
         */
        if (progress.getStatus()
                == EmployeeTrainingProgress.TrainingStatus.COMPLETED) {

            return true;
        }

        /*
         * Secondary completion state.
         */
        Integer percentage =
                progress.getProgressPercentage();

        return percentage != null
                && percentage >= 100;
    }


    // =========================================================
    // SAFE PROGRESS
    // =========================================================

    private int getSafeProgress(
            EmployeeTrainingProgress progress
    ) {

        if (progress == null
                || progress.getProgressPercentage() == null) {

            return 0;
        }

        return clampPercentage(
                progress.getProgressPercentage()
        );
    }


    // =========================================================
    // TOTAL MODULE COUNT
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public int getTotalModuleCount(
            User employee
    ) {

        if (employee == null) {
            return 0;
        }

        Optional<LearningPath> learningPath =
                getActiveLearningPath(employee);

        if (learningPath.isEmpty()) {

            learningPath =
                    getLatestLearningPath(employee);
        }

        if (learningPath.isEmpty()) {
            return 0;
        }

        long count =
                learningPathItemRepository
                        .countByLearningPath(
                                learningPath.get()
                        );

        if (count > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }

        return (int) count;
    }


    // =========================================================
    // FIND PROGRESS BY ID
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public Optional<EmployeeTrainingProgress> findProgressById(
            Long id
    ) {

        if (id == null) {
            return Optional.empty();
        }

        return employeeTrainingProgressRepository
                .findById(id);
    }


    // =========================================================
    // GET OR CREATE PROGRESS
    // =========================================================

    private EmployeeTrainingProgress getOrCreateProgress(
            User employee,
            LearningPathItem learningPathItem
    ) {

        Optional<EmployeeTrainingProgress> existing =
                employeeTrainingProgressRepository
                        .findByEmployeeAndLearningPathItem(
                                employee,
                                learningPathItem
                        );

        if (existing.isPresent()) {
            return existing.get();
        }

        LocalDateTime now =
                LocalDateTime.now();

        return employeeTrainingProgressRepository.save(
                EmployeeTrainingProgress.builder()
                        .employee(employee)
                        .learningPathItem(
                                learningPathItem
                        )
                        .progressPercentage(0)
                        .status(
                                EmployeeTrainingProgress
                                        .TrainingStatus
                                        .NOT_STARTED
                        )
                        .started(false)
                        .timeSpentMinutes(0)
                        .createdAt(now)
                        .updatedAt(now)
                        .build()
        );
    }


    // =========================================================
    // UPDATE LEARNING PATH AGGREGATE
    // =========================================================

    private void updateLearningPathProgress(
            LearningPath learningPath
    ) {

        if (learningPath == null
                || learningPath.getId() == null) {

            return;
        }

        List<LearningPathItem> items =
                learningPathItemRepository
                        .findByLearningPathOrderBySequenceOrderAsc(
                                learningPath
                        );

        if (items == null || items.isEmpty()) {

            learningPath.setProgressPercentage(0);

            learningPathRepository.save(
                    learningPath
            );

            return;
        }

        List<EmployeeTrainingProgress> progressRecords =
                employeeTrainingProgressRepository
                        .findByEmployeeAndLearningPathItemIn(
                                learningPath.getEmployee(),
                                items
                        );

        if (progressRecords == null) {

            progressRecords =
                    Collections.emptyList();
        }

        Map<Long, Integer> progressMap =
                new HashMap<>();

        for (EmployeeTrainingProgress progress :
                progressRecords) {

            if (progress == null
                    || progress.getLearningPathItem() == null
                    || progress.getLearningPathItem().getId() == null) {

                continue;
            }

            Long itemId =
                    progress.getLearningPathItem().getId();

            Integer percentage =
                    progress.getProgressPercentage();

            if (percentage == null) {
                percentage = 0;
            }

            percentage =
                    clampPercentage(
                            percentage
                    );

            Integer existing =
                    progressMap.get(itemId);

            if (existing == null
                    || percentage > existing) {

                progressMap.put(
                        itemId,
                        percentage
                );
            }
        }


        int totalProgress = 0;

        int validItems = 0;


        for (LearningPathItem item : items) {

            if (item == null
                    || item.getId() == null) {

                continue;
            }

            int percentage =
                    progressMap.getOrDefault(
                            item.getId(),
                            0
                    );


            // =================================================
            // SYNCHRONIZE ITEM PROGRESS
            // =================================================

            item.setProgressPercentage(
                    percentage
            );


            // =================================================
            // SYNCHRONIZE ITEM STATUS
            // =================================================

            if (percentage >= 100) {

                item.setStatus(
                        LearningPathItem.ItemStatus
                                .COMPLETED
                );

            } else if (percentage > 0) {

                item.setStatus(
                        LearningPathItem.ItemStatus
                                .IN_PROGRESS
                );

            } else {

                item.setStatus(
                        LearningPathItem.ItemStatus
                                .NOT_STARTED
                );
            }


            totalProgress += percentage;

            validItems++;
        }


        // =====================================================
        // CALCULATE OVERALL LEARNING PATH PROGRESS
        // =====================================================

        int overallProgress = 0;

        if (validItems > 0) {

            overallProgress =
                    Math.round(
                            (float) totalProgress
                                    / validItems
                    );
        }


        learningPath.setProgressPercentage(
                clampPercentage(
                        overallProgress
                )
        );

        learningPath.setUpdatedAt(
                LocalDateTime.now()
        );


        learningPathRepository.save(
                learningPath
        );
    }


    // =========================================================
    // VALIDATE EMPLOYEE + ITEM
    // =========================================================

    private void validateEmployeeAndItem(
            User employee,
            LearningPathItem learningPathItem
    ) {

        if (employee == null) {

            throw new IllegalArgumentException(
                    "Employee is required."
            );
        }

        if (learningPathItem == null) {

            throw new IllegalArgumentException(
                    "Learning path item is required."
            );
        }

        LearningPath learningPath =
                learningPathItem.getLearningPath();

        if (learningPath == null) {

            throw new IllegalArgumentException(
                    "Learning path item is not associated "
                            + "with a learning path."
            );
        }

        if (learningPath.getEmployee() == null
                || !sameUser(
                        employee,
                        learningPath.getEmployee()
                )) {

            throw new IllegalArgumentException(
                    "This training module does not belong "
                            + "to the logged-in employee."
            );
        }
    }


    // =========================================================
    // CHECK EMPLOYEE OWNERSHIP
    // =========================================================

    private boolean belongsToEmployee(
            User employee,
            LearningPathItem learningPathItem
    ) {

        if (employee == null
                || learningPathItem == null
                || learningPathItem.getLearningPath() == null) {

            return false;
        }

        return sameUser(
                employee,
                learningPathItem
                        .getLearningPath()
                        .getEmployee()
        );
    }


    // =========================================================
    // COMPARE USERS
    // =========================================================

    private boolean sameUser(
            User first,
            User second
    ) {

        if (first == null || second == null) {
            return false;
        }

        if (first.getId() != null
                && second.getId() != null) {

            return first.getId()
                    .equals(
                            second.getId()
                    );
        }

        if (first.getEmail() != null
                && second.getEmail() != null) {

            return first.getEmail()
                    .equalsIgnoreCase(
                            second.getEmail()
                    );
        }

        return false;
    }


    // =========================================================
    // CLAMP PERCENTAGE
    // =========================================================

    private int clampPercentage(
            Integer value
    ) {

        if (value == null) {
            return 0;
        }

        return Math.max(
                0,
                Math.min(
                        100,
                        value
                )
        );
    }
}