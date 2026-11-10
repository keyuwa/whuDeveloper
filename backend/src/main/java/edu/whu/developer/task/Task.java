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

    /** TA / INTERN / RA */
    @Column(name = "role_type", nullable = false, length = 16)
    private String roleType = "INTERN";

    /** 各任务类型内的独立序号，从 1 起。 */
    @Column(name = "role_serial", nullable = false)
    private Integer roleSerial;

    /** 对外编号，如 TA-0001 / DEV-0001 / RA-0001。 */
    @Column(name = "public_code", nullable = false, length = 32)
    private String publicCode;

    @Column(name = "tech_field", nullable = false, length = 64)
    private String techField;

    @Column(nullable = false, length = 8)
    private String difficulty;

    @Column(nullable = false, columnDefinition = "text")
    private String background;

    @Column(name = "tech_stack", nullable = false, length = 300)
    private String techStack;

    @Column(name = "programming_languages", nullable = false, length = 300)
    private String programmingLanguages;

    @Column(name = "support_languages", nullable = false, length = 64)
    private String supportLanguages;

    @Column(name = "output_requirement", nullable = false, columnDefinition = "text")
    private String outputRequirement;

    @Column(name = "tech_requirement", nullable = false, columnDefinition = "text")
    private String techRequirement;

    @Column(name = "duration_weeks", nullable = false)
    private Integer durationWeeks;

    /** TA/RA 学期或科研周期；INTERN 可为空。 */
    @Column(name = "schedule_type", length = 32)
    private String scheduleType;

    /** 助教面向的在读身份，逗号分隔：本科生/硕士生/博士生。 */
    @Column(name = "education_levels", length = 64)
    private String educationLevels;

    @Column(name = "estimated_hours", nullable = false)
    private Integer estimatedHours;

    @Column(name = "repo_url", length = 500)
    private String repoUrl;

    @Column(name = "learning_materials", columnDefinition = "text")
    private String learningMaterials;

    @Column(name = "stipend_text", nullable = false, length = 500)
    private String stipendText;

    @Column(name = "prd_md", columnDefinition = "text")
    private String prdMd;

    @Column(name = "prd_file_url", length = 500)
    private String prdFileUrl;

    @Column(name = "prd_file_name", length = 255)
    private String prdFileName;

    @Column(name = "tech_doc_md", columnDefinition = "text")
    private String techDocMd;

    @Column(name = "tech_doc_file_url", length = 500)
    private String techDocFileUrl;

    @Column(name = "tech_doc_file_name", length = 255)
    private String techDocFileName;

    @Column(nullable = false)
    private Integer quota;

    @Column(nullable = false, length = 32)
    private String status;

    /** STANDARD | CHALLENGE（揭榜） */
    @Column(name = "posting_mode", nullable = false, length = 16)
    private String postingMode = "STANDARD";

    /** 揭榜截止时间；仅 CHALLENGE 使用 */
    @Column(name = "bid_deadline")
    private LocalDateTime bidDeadline;

    /** OPEN | SELECTING | AWARDED | CLOSED；仅 CHALLENGE 使用 */
    @Column(name = "challenge_status", length = 16)
    private String challengeStatus;

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
        if (roleType == null) {
            roleType = "INTERN";
        }
        if (postingMode == null) {
            postingMode = "STANDARD";
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
