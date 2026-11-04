package edu.whu.developer.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role role;

    @Column(name = "display_name", nullable = false, length = 64)
    private String displayName;

    @Column(name = "real_name", length = 64)
    private String realName;

    @Column(length = 64)
    private String school;

    @Column(length = 64)
    private String college;

    @Column(length = 64)
    private String major;

    /** 本科生 / 硕士生 / 博士生 */
    @Column(name = "student_identity", length = 16)
    private String studentIdentity;

    @Column(length = 500)
    private String skills;

    @Column(columnDefinition = "text")
    private String bio;

    @Column(name = "resume_file_url", length = 500)
    private String resumeFileUrl;

    @Column(name = "resume_file_name", length = 255)
    private String resumeFileName;

    @Column(name = "ability_level", length = 8)
    private String abilityLevel;

    @Column(nullable = false)
    private int points = 0;

    @Column(name = "ability_apprentice", nullable = false)
    private boolean abilityApprentice = false;

    @Column(name = "ability_mgmt", nullable = false)
    private boolean abilityMgmt = false;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = "ACTIVE";
        }
    }
}
