package com.capacitix.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "competencies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Competency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(nullable = false, length = 150)
    private String name;


    @Column(length = 1000)
    private String description;


    @Column(nullable = false, length = 100)
    private String category;


    @Column(nullable = false, length = 50)
    private String level;


    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";


    /*
     * =========================================================
     * MAPPED EMPLOYEES
     * =========================================================
     *
     * This is derived data.
     *
     * It is NOT stored in the competencies table.
     *
     * The AdminCompetencyController calculates this value
     * dynamically from AssessmentAssignment +
     * AssessmentQuestion.
     *
     * Example:
     *
     * Java OOP
     *
     * Prashant -> Assessment 1
     * Prashant -> Assessment 2
     * Rahul    -> Assessment 3
     *
     * Mapped employees = 2
     *
     * DISTINCT employees are counted.
     */

    @Transient
    @Builder.Default
    private Integer mappedEmployees = 0;
}