package edu.whu.developer.task;

import edu.whu.developer.auth.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "publisher_id", nullable = false)
    private User publisher;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "role_type", nullable = false, length = 16)
    private String roleType;

    @Column(nullable = false, length = 8)
    private String difficulty;

    @Column(nullable = false, columnDefinition = "text")
    private String background;

    @Column(name = "tech_stack", nullable = false, length = 300)
    private String techStack;

    @Column(name = "output_requirement", nullable = false, columnDefinition = "text")
    private String outputRequirement;

    @Column(name = "tech_requirement", nullable = false, columnDefinition = "text")
    private String techRequirement;

    @Column(name = "duration_weeks", nullable = false)
    private Integer durationWeeks;

    @Column(name = "estimated_hours", nullable = false)
    private Integer estimatedHours;

    @Column(name = "repo_url", length = 500)
    private String repoUrl;

    @Column(name = "learning_materials", columnDefinition = "text")
    private String learningMaterials;

    @Column(name = "stipend_text", nullable = false, length = 500)
    private String stipendText;

    @Column(name = "prd_md", nullable = false, columnDefinition = "text")
    private String prdMd;

    @Column(name = "tech_doc_md", columnDefinition = "text")
    private String techDocMd;

    @Column(nullable = false)
    private Integer quota;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (status == null) {
            status = "DRAFT";
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
