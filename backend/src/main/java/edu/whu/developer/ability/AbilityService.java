package edu.whu.developer.ability;

import edu.whu.developer.admin.SettingService;
import edu.whu.developer.auth.Role;
import edu.whu.developer.auth.User;
import edu.whu.developer.auth.UserRepository;
import edu.whu.developer.common.AuthSupport;
import edu.whu.developer.common.AuthUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AbilityService {

    private static final List<String> OPEN_STATUSES = List.of("SUBMITTED", "IN_REVIEW", "NEED_INFO");

    private final AbilityApplicationRepository applications;
    private final UserRepository users;
    private final PointLedgerRepository ledger;
    private final PointService points;
    private final SettingService settings;

    public AbilityService(AbilityApplicationRepository applications,
                          UserRepository users,
                          PointLedgerRepository ledger,
                          PointService points,
                          SettingService settings) {
        this.applications = applications;
        this.users = users;
        this.ledger = ledger;
        this.points = points;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public String rubricMarkdown() {
        String configured = settings.getString("ability_rubric_md", "");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        try {
            return StreamUtils.copyToString(
                    new ClassPathResource("ability-assessment.md").getInputStream(),
                    StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "# 能力裁定细则\n\n请在管理后台「设置」中配置 Top Dev 裁定细则 Markdown。";
        }
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntry> leaderboard() {
        List<User> top = users.findTop50ByRoleOrderByPointsDescIdAsc(Role.STUDENT);
        java.util.ArrayList<LeaderboardEntry> rows = new java.util.ArrayList<>();
        int rank = 1;
        for (User user : top) {
            rows.add(new LeaderboardEntry(
                    rank++,
                    user.getId(),
                    user.getDisplayName(),
                    AbilityLabels.label(user),
                    user.getAbilityLevel() == null ? "LV0" : user.getAbilityLevel(),
                    user.isAbilityApprentice(),
                    user.isAbilityMgmt(),
                    user.getPoints()));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public MyAbilityView mine() {
        AuthUser current = AuthSupport.current();
        User user = users.findById(current.getId()).orElseThrow();
        Integer myRank = null;
        if (user.getRole() == Role.STUDENT) {
            myRank = (int) users.countStudentsAhead(user.getPoints(), user.getId()) + 1;
        }
        List<PointRow> recent = ledger.findTop20ByUserIdOrderByIdDesc(user.getId()).stream()
                .map(item -> new PointRow(item.getId(), item.getDelta(), item.getBalanceAfter(), item.getReason(), item.getCreatedAt()))
                .toList();
        return new MyAbilityView(
                user.getAbilityLevel() == null ? "LV0" : user.getAbilityLevel(),
                user.isAbilityApprentice(),
                user.isAbilityMgmt(),
                AbilityLabels.label(user),
                user.getPoints(),
                myRank,
                AbilityLabels.canJudge(user),
                recent,
                applications.findByApplicantIdOrderByIdDesc(user.getId()).stream().map(this::toView).toList());
    }

    @Transactional
    public AbilityApplicationView apply(ApplyRequest request) {
        AuthUser current = AuthSupport.requireRole(Role.STUDENT);
        User applicant = users.findById(current.getId()).orElseThrow();
        String target = AbilityLabels.normalize(request.targetLevel());
        if (applications.existsByApplicantIdAndStatusIn(applicant.getId(), OPEN_STATUSES)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "你已有进行中的能力裁定申请");
        }
        LocalDateTime coolSince = LocalDateTime.now().minusDays(14);
        if (applications.existsByApplicantIdAndTargetLevelAndWantApprenticeAndWantMgmtAndStatusAndDecidedAtAfter(
                applicant.getId(), target, request.wantApprentice(), request.wantMgmt(), "REJECTED", coolSince)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "相同目标在驳回后 14 日内不可重复申请");
        }
        String assessment = request.selfAssessment().trim();
        if (assessment.length() < 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "对照细则自评请至少 100 字");
        }
        AbilityApplication app = new AbilityApplication();
        app.setApplicant(applicant);
        app.setTargetLevel(target);
        app.setWantApprentice(request.wantApprentice());
        app.setWantMgmt(request.wantMgmt());
        app.setSelfAssessment(assessment);
        app.setEvidence(blank(request.evidence()));
        app.setStatus("SUBMITTED");
        return toView(applications.save(app));
    }

    @Transactional(readOnly = true)
    public List<AbilityApplicationView> myApplications() {
        AuthUser current = AuthSupport.requireRole(Role.STUDENT);
        return applications.findByApplicantIdOrderByIdDesc(current.getId()).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<AbilityApplicationView> queue() {
        User judge = requireJudge();
        return applications.findByStatusInOrderByIdAsc(OPEN_STATUSES).stream()
                .filter(item -> !item.getApplicant().getId().equals(judge.getId()))
                .map(this::toView)
                .toList();
    }

    @Transactional
    public AbilityApplicationView claim(Long id) {
        User judge = requireJudge();
        AbilityApplication app = applications.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "申请不存在"));
        if (app.getApplicant().getId().equals(judge.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能裁定自己的申请");
        }
        if (!"SUBMITTED".equals(app.getStatus()) && !"NEED_INFO".equals(app.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前状态不可认领");
        }
        if (app.getReviewer() != null && !app.getReviewer().getId().equals(judge.getId()) && judge.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "申请已被他人认领");
        }
        app.setReviewer(judge);
        app.setStatus("IN_REVIEW");
        return toView(applications.save(app));
    }

    @Transactional
    public AbilityApplicationView requestInfo(Long id, CommentRequest request) {
        User judge = requireJudge();
        AbilityApplication app = ownedForReview(id, judge);
        app.setStatus("NEED_INFO");
        app.setDecideComment(blank(request.comment()));
        return toView(applications.save(app));
    }

    @Transactional
    public AbilityApplicationView decide(Long id, DecideRequest request) {
        User judge = requireJudge();
        AbilityApplication app = ownedForReview(id, judge);
        if (!"IN_REVIEW".equals(app.getStatus()) && !"NEED_INFO".equals(app.getStatus()) && !"SUBMITTED".equals(app.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前状态不可终裁");
        }
        String decision = request.decision().trim().toUpperCase();
        if ("REJECT".equals(decision)) {
            app.setStatus("REJECTED");
            app.setDecideComment(blank(request.comment()));
            app.setDecidedAt(LocalDateTime.now());
            if (app.getReviewer() == null) {
                app.setReviewer(judge);
            }
            return toView(applications.save(app));
        }
        if (!"APPROVE".equals(decision)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "decision 须为 APPROVE 或 REJECT");
        }
        String resultLevel = AbilityLabels.normalize(request.resultLevel() == null ? app.getTargetLevel() : request.resultLevel());
        if (AbilityLabels.rankValue(resultLevel) > AbilityLabels.rankValue(app.getTargetLevel())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "改判结果不能高于申请目标档");
        }
        boolean apprentice = request.resultApprentice() != null ? request.resultApprentice() : app.isWantApprentice();
        boolean mgmt = request.resultMgmt() != null ? request.resultMgmt() : app.isWantMgmt();
        User applicant = app.getApplicant();
        applicant.setAbilityLevel(resultLevel);
        applicant.setAbilityApprentice(apprentice);
        applicant.setAbilityMgmt(mgmt);
        users.save(applicant);

        app.setStatus("APPROVED");
        app.setResultLevel(resultLevel);
        app.setResultApprentice(apprentice);
        app.setResultMgmt(mgmt);
        app.setDecideComment(blank(request.comment()));
        app.setDecidedAt(LocalDateTime.now());
        if (app.getReviewer() == null) {
            app.setReviewer(judge);
        }
        return toView(applications.save(app));
    }

    @Transactional
    public AbilityApplicationView withdraw(Long id) {
        AuthUser current = AuthSupport.requireRole(Role.STUDENT);
        AbilityApplication app = applications.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "申请不存在"));
        if (!app.getApplicant().getId().equals(current.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能撤回自己的申请");
        }
        if (!OPEN_STATUSES.contains(app.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前状态不可撤回");
        }
        app.setStatus("WITHDRAWN");
        return toView(applications.save(app));
    }

    @Transactional
    public void adminAdjustPoints(Long userId, PointsAdjustRequest request) {
        AuthUser admin = AuthSupport.requireRole(Role.ADMIN);
        if (request.delta() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "调整值不能为 0");
        }
        String reason = request.reason().trim();
        points.award(
                userId,
                request.delta(),
                reason,
                "ADMIN_ADJUST",
                String.valueOf(System.currentTimeMillis()),
                "ADMIN_ADJUST:" + userId + ":" + System.nanoTime(),
                admin.getId());
    }

    @Transactional
    public void adminSetAbility(Long userId, AdminAbilityRequest request) {
        AuthSupport.requireRole(Role.ADMIN);
        User user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        user.setAbilityLevel(AbilityLabels.normalize(request.abilityLevel()));
        user.setAbilityApprentice(request.apprentice());
        user.setAbilityMgmt(request.mgmt());
        users.save(user);
    }

    private AbilityApplication ownedForReview(Long id, User judge) {
        AbilityApplication app = applications.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "申请不存在"));
        if (app.getApplicant().getId().equals(judge.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能裁定自己的申请");
        }
        if (app.getReviewer() != null && !app.getReviewer().getId().equals(judge.getId()) && judge.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "请先认领该申请");
        }
        if (app.getReviewer() == null) {
            app.setReviewer(judge);
        }
        return app;
    }

    private User requireJudge() {
        AuthUser current = AuthSupport.current();
        User user = users.findById(current.getId()).orElseThrow();
        if (!AbilityLabels.canJudge(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员或 LV4/LV5 可裁定");
        }
        return user;
    }

    private AbilityApplicationView toView(AbilityApplication app) {
        User applicant = app.getApplicant();
        User reviewer = app.getReviewer();
        return new AbilityApplicationView(
                app.getId(),
                applicant.getId(),
                applicant.getDisplayName(),
                AbilityLabels.label(applicant),
                app.getTargetLevel(),
                app.isWantApprentice(),
                app.isWantMgmt(),
                AbilityLabels.label(app.getTargetLevel(), app.isWantApprentice(), app.isWantMgmt()),
                app.getSelfAssessment(),
                app.getEvidence(),
                app.getStatus(),
                reviewer == null ? null : reviewer.getId(),
                reviewer == null ? null : (reviewer.getRole() == Role.ADMIN
                        ? (reviewer.getRealName() == null ? reviewer.getDisplayName() : reviewer.getRealName())
                        : reviewer.getDisplayName()),
                app.getResultLevel(),
                app.getResultApprentice(),
                app.getResultMgmt(),
                app.getResultLevel() == null ? null : AbilityLabels.label(
                        app.getResultLevel(),
                        Boolean.TRUE.equals(app.getResultApprentice()),
                        Boolean.TRUE.equals(app.getResultMgmt())),
                app.getDecideComment(),
                app.getCreatedAt(),
                app.getDecidedAt());
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record LeaderboardEntry(
            int rank,
            Long userId,
            String displayName,
            String abilityLabel,
            String abilityLevel,
            boolean abilityApprentice,
            boolean abilityMgmt,
            int points
    ) {
    }

    public record PointRow(Long id, int delta, int balanceAfter, String reason, LocalDateTime createdAt) {
    }

    public record MyAbilityView(
            String abilityLevel,
            boolean abilityApprentice,
            boolean abilityMgmt,
            String abilityLabel,
            int points,
            Integer myRank,
            boolean canJudge,
            List<PointRow> recentPoints,
            List<AbilityApplicationView> applications
    ) {
    }

    public record AbilityApplicationView(
            Long id,
            Long applicantId,
            String applicantName,
            String applicantAbilityLabel,
            String targetLevel,
            boolean wantApprentice,
            boolean wantMgmt,
            String targetLabel,
            String selfAssessment,
            String evidence,
            String status,
            Long reviewerId,
            String reviewerName,
            String resultLevel,
            Boolean resultApprentice,
            Boolean resultMgmt,
            String resultLabel,
            String decideComment,
            LocalDateTime createdAt,
            LocalDateTime decidedAt
    ) {
    }

    public record ApplyRequest(
            @NotBlank String targetLevel,
            boolean wantApprentice,
            boolean wantMgmt,
            @NotBlank @Size(min = 100, max = 5000) String selfAssessment,
            @Size(max = 5000) String evidence
    ) {
    }

    public record CommentRequest(@Size(max = 1000) String comment) {
    }

    public record DecideRequest(
            @NotBlank String decision,
            String resultLevel,
            Boolean resultApprentice,
            Boolean resultMgmt,
            @Size(max = 1000) String comment
    ) {
    }

    public record PointsAdjustRequest(@NotNull Integer delta, @NotBlank @Size(max = 200) String reason) {
    }

    public record AdminAbilityRequest(
            @NotBlank String abilityLevel,
            boolean apprentice,
            boolean mgmt
    ) {
    }
}
