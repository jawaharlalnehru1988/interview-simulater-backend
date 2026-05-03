package com.asknehru.interviewsimulator.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "interviews_candidateprofile")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "current_position")
    private String currentPosition;

    @Column(name = "current_company")
    private String currentCompany;

    @Column(name = "total_experience_years", precision = 4, scale = 1)
    private BigDecimal totalExperienceYears;

    @Column(name = "primary_skills", columnDefinition = "TEXT")
    private String primarySkills; // JSON

    @Column(name = "current_salary")
    private String currentSalary;

    @Column(name = "salary_expectation")
    private String salaryExpectation;

    @Column(name = "notice_period")
    private String noticePeriod;

    @Column(name = "reason_for_leaving", columnDefinition = "TEXT")
    private String reasonForLeaving;

    @Column(name = "career_gap_details", columnDefinition = "TEXT")
    private String careerGapDetails;

    @Column(name = "highest_education")
    private String highestEducation;

    @Column(name = "preferred_locations", columnDefinition = "TEXT")
    private String preferredLocations; // JSON

    @Column(name = "preferred_role")
    private String preferredRole;

    @Column(name = "additional_notes", columnDefinition = "TEXT")
    private String additionalNotes;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
