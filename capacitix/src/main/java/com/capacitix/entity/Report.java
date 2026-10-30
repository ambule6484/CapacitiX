package com.capacitix.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(name = "generated_by", nullable = false, length = 150)
    @Builder.Default
    private String generatedBy = "Administrator";

    @Column(name = "generated_date", nullable = false)
    @Builder.Default
    private LocalDateTime generatedDate = LocalDateTime.now();

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "READY";

    public String getBadgeClass() {

        if (type == null) {
            return "badge-employee";
        }

        return switch (type.toLowerCase()) {

            case "training" ->
                    "badge-training";

            case "skill gap" ->
                    "badge-skill";

            case "assessment" ->
                    "badge-assessment";

            case "employee" ->
                    "badge-employee";

            case "competency" ->
                    "badge-skill";

            case "training impact" ->
                    "badge-training";

            default ->
                    "badge-employee";
        };
    }

    public String getFormattedDate() {

        if (generatedDate == null) {
            return "";
        }

        return generatedDate
                .toLocalDate()
                .toString();
    }
}