package edu.whu.developer.application;

import edu.whu.developer.auth.User;
import edu.whu.developer.task.Task;
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
@Table(name = "applications")
public class TaskApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "proposal_md", nullable = false, columnDefinition = "text")
    private String proposalMd;

    @Column(name = "proposal_file_name", length = 255)
    private String proposalFileName;

    @Column(name = "proposal_pdf", columnDefinition = "mediumblob")
    private byte[] proposalPdf;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "interview_at")
    private LocalDateTime interviewAt;

    @Column(name = "interview_note", length = 500)
    private String interviewNote;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(name = "remind_count", nullable = false)
    private int remindCount;

    /** AI 辅助评分 0–100；可空 */
    @Column(name = "ai_score")
    private Integer aiScore;

    @Column(name = "ai_summary", length = 500)
    private String aiSummary;

    @Column(name = "ai_detail_json", columnDefinition = "text")
    private String aiDetailJson;

    /** PENDING | DONE | SKIPPED | FAILED */
    @Column(name = "ai_status", nullable = false, length = 16)
    private String aiStatus = "SKIPPED";

    @Column(name = "ai_scored_at")
    private LocalDateTime aiScoredAt;

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
            status = "SUBMITTED";
        }
        if (aiStatus == null) {
            aiStatus = "SKIPPED";
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
