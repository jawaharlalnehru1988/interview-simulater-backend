package com.asknehru.interviewsimulator.document;
import com.asknehru.interviewsimulator.auth.User;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "documents_document")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    public enum DocumentType {
        PDF, WORD, EXCEL, IMAGE, POWERPOINT, TEXT, OTHER
    }

    public enum OldOrNew {
        NEW, OLD
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", length = 50)
    @Builder.Default
    private DocumentType documentType = DocumentType.OTHER;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private DocumentCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_or_new", length = 10)
    @Builder.Default
    private OldOrNew oldOrNew = OldOrNew.NEW;

    @Column(nullable = false)
    private String file; // Path to the file

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "family_member", length = 100)
    @Builder.Default
    private String familyMember = "Self";

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @JsonProperty("categoryId")
    public Long getCategoryId() {
        return category != null ? category.getId() : null;
    }

    @JsonProperty("categoryName")
    public String getCategoryName() {
        return category != null ? category.getName() : null;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
        if (familyMember == null || familyMember.isBlank()) familyMember = "Self";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
