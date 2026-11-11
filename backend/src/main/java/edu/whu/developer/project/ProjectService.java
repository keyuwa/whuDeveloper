package edu.whu.developer.project;

import edu.whu.developer.ability.PointService;
import edu.whu.developer.application.TaskApplication;
import edu.whu.developer.auth.Role;
import edu.whu.developer.common.AuthSupport;
import edu.whu.developer.common.AuthUser;
import edu.whu.developer.completion.Completion;
import edu.whu.developer.completion.CompletionRepository;
import edu.whu.developer.notify.AuditService;
import edu.whu.developer.notify.NotificationService;
import edu.whu.developer.report.WeeklyReport;
import edu.whu.developer.report.WeeklyReportRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
public class ProjectService {

    // Application review establishes scope and design before enrollment.
    private static final MilestoneTemplate DELIVERY = new MilestoneTemplate(
            "开发交付", "按申请书约定完成功能，留下可审阅的代码变更。",
            "提交：Pull Request 链接 + 自测说明");

    private final ProjectRepository projects;
    private final MilestoneRepository milestones;
    private final WeeklyReportRepository reports;
    private final CompletionRepository completions;
    private final AuditService audit;
    private final NotificationService notifications;
    private final PointService points;

    public ProjectService(ProjectRepository projects,
                          MilestoneRepository milestones,
                          WeeklyReportRepository reports,
                          CompletionRepository completions,
                          AuditService audit,
                          NotificationService notifications,
                          PointService points) {
        this.projects = projects;
        this.milestones = milestones;
        this.reports = reports;
        this.completions = completions;
        this.audit = audit;
        this.notifications = notifications;
        this.points = points;
    }

    @Transactional
    public ProjectResponse openFromApplication(TaskApplication application) {
        return projects.findByApplicationId(application.getId())
                .map(this::toResponse)
                .orElseGet(() -> {
                    if (application.getPosition() != null) {
                        long positionUsed = projects.countByPositionIdAndStatusNot(
                                application.getPosition().getId(), "TERMINATED");
                        if (positionUsed >= application.getPosition().getQuota()) {
                            throw new ResponseStatusException(HttpStatus.CONFLICT, "该岗位名额已满");
                        }
                    }
                    long used = projects.countByTaskIdAndStatusNot(application.getTask().getId(), "TERMINATED");
                    if (used >= application.getTask().getQuota()) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "名额已满");
                    }
                    Project project = new Project();
                    project.setApplication(application);
                    project.setTask(application.getTask());
                    project.setStudent(application.getStudent());
                    project.setPublisher(application.getTask().getPublisher());
                    project.setStatus("ACTIVE");
                    projects.save(project);
                    // Scope is approved with the application; deliverables are reviewed at completion.
                    audit.record(AuthSupport.current().getId(), "PROJECT", project.getId(), "ENROLL", application.getTask().getTitle());
                    return toResponse(project);
                });
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> mine() {
        AuthUser user = AuthSupport.current();
        List<Project> found = user.getRole() == Role.PUBLISHER
                ? projects.findByPublisherIdOrderByIdDesc(user.getId())
                : projects.findByStudentIdOrderByIdDesc(user.getId());
        return found.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(Long id) {
        return toResponse(visible(id));
    }

    @Transactional
    public ProjectResponse leave(Long id) {
        AuthUser student = AuthSupport.requireRole(Role.STUDENT);
        Project project = visible(id);
        if (!project.getStudent().getId().equals(student.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能取消自己的项目");
        }
        if (!"ACTIVE".equals(project.getStatus()) && !"COMPLETING".equals(project.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前项目状态不可取消");
        }
        project.setStatus("TERMINATED");
        notifications.push(
                project.getPublisher().getId(),
                "学生已取消协作",
                student.getDisplayName() + " 已取消「" + project.getTask().getTitle() + "」的协作。");
        audit.record(student.getId(), "PROJECT", project.getId(), "LEAVE", project.getTask().getTitle());
        return toResponse(project);
    }

    @Transactional
    public ProjectResponse submitMilestone(Long projectId, Long milestoneId, MilestoneSubmit request) {
        AuthUser student = AuthSupport.requireRole(Role.STUDENT);
        Project project = visible(projectId);
        if (!project.getStudent().getId().equals(student.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有本项目学生可以提交节点");
        }
        if ("COMPLETED".equals(project.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "项目已结项");
        }
        Milestone milestone = milestone(projectId, milestoneId);
        if (!"IN_PROGRESS".equals(milestone.getStatus()) && !"REJECTED".equals(milestone.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前节点还不能提交");
        }
        milestone.setNote(request.note().trim());
        milestone.setDeliverableUrl(blank(request.deliverableUrl()));
        milestone.setStatus("SUBMITTED");
        audit.record(student.getId(), "MILESTONE", milestone.getId(), "SUBMIT", milestone.getTitle());
        return toResponse(project);
    }

    @Transactional
    public ProjectResponse reviewMilestone(Long projectId, Long milestoneId, ReviewRequest request) {
        AuthUser publisher = AuthSupport.requireRole(Role.PUBLISHER);
        Project project = visible(projectId);
        if (!project.getPublisher().getId().equals(publisher.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能审核自己的项目");
        }
        Milestone milestone = milestone(projectId, milestoneId);
        if (!"SUBMITTED".equals(milestone.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "节点还没有待审提交");
        }
        if ("APPROVE".equals(request.decision())) {
            milestone.setStatus("APPROVED");
            advance(projectId, milestone.getSeq());
            audit.record(publisher.getId(), "MILESTONE", milestone.getId(), "APPROVE", milestone.getTitle());
        } else if ("REJECT".equals(request.decision())) {
            milestone.setStatus("REJECTED");
            audit.record(publisher.getId(), "MILESTONE", milestone.getId(), "REJECT", request.note());
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "决定只能是通过或驳回");
        }
        return toResponse(project);
    }

    @Transactional
    public ProjectResponse submitReport(Long projectId, ReportRequest request) {
        AuthUser student = AuthSupport.requireRole(Role.STUDENT);
        Project project = visible(projectId);
        if (!project.getStudent().getId().equals(student.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有本项目学生可以提交周报");
        }
        if (!"ACTIVE".equals(project.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "项目进行中才能提交周报");
        }
        int nextIndex = reports.findByProjectIdOrderByWeekIndexAsc(projectId).stream()
                .mapToInt(WeeklyReport::getWeekIndex).max().orElse(0) + 1;
        WeeklyReport report = new WeeklyReport();
        report.setProject(project);
        report.setWeekIndex(nextIndex);
        report.setContent(request.content().trim());
        report.setBlockers(blank(request.blockers()));
        report.setPlan(blank(request.plan()));
        report.setSupportNeeded(blank(request.supportNeeded()));
        report.setStatus("SUBMITTED");
        reports.save(report);
        points.award(
                student.getId(),
                5,
                "提交周报：第 " + nextIndex + " 周",
                "REPORT_SUBMIT",
                String.valueOf(report.getId()),
                "REPORT_SUBMIT:" + report.getId());
        audit.record(student.getId(), "REPORT", report.getId(), "SUBMIT", String.valueOf(nextIndex));
        return toResponse(project);
    }

    @Transactional
    public ProjectResponse scoreReport(Long projectId, Long reportId, ScoreRequest request) {
        AuthUser publisher = AuthSupport.requireRole(Role.PUBLISHER);
        Project project = visible(projectId);
        if (!project.getPublisher().getId().equals(publisher.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能给自己的项目反馈周报");
        }
        WeeklyReport report = reports.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "周报不存在"));
        if (!report.getProject().getId().equals(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "周报不存在");
        }
        report.setScore(request.score());
        report.setScoreComment(blank(request.comment()));
        report.setStatus("SCORED");
        report.setScoredAt(LocalDateTime.now());
        String detail = request.score() == null ? "READ" : String.valueOf(request.score());
        if (request.score() != null) {
            points.award(
                    project.getStudent().getId(),
                    request.score() * 3,
                    "周报获评 " + request.score() + " 分",
                    "REPORT_SCORE",
                    String.valueOf(report.getId()),
                    "REPORT_SCORE:" + report.getId());
        }
        audit.record(publisher.getId(), "REPORT", report.getId(), "SCORE", detail);
        return toResponse(project);
    }

    @Transactional
    public ProjectResponse submitCompletion(Long projectId, CompletionRequest request) {
        AuthUser student = AuthSupport.requireRole(Role.STUDENT);
        Project project = visible(projectId);
        if (!project.getStudent().getId().equals(student.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有本项目学生可以提交结项");
        }
        String reportMd = blank(request.reportMd());
        String reportFileUrl = blank(request.reportFileUrl());
        if (reportMd == null && reportFileUrl == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写结项文字材料或上传 PDF");
        }
        List<String> urls = request.prUrls() == null
                ? List.of()
                : request.prUrls().stream().map(String::trim).filter(url -> !url.isBlank()).toList();
        if (urls.stream().anyMatch(url -> !url.startsWith("http://") && !url.startsWith("https://"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "代码仓库 / PR 链接须为 http(s) 地址");
        }
        Completion completion = completions.findByProjectId(projectId).orElseGet(Completion::new);
        if ("APPROVED".equals(completion.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "结项已经通过");
        }
        completion.setProject(project);
        completion.setReportMd(reportMd == null ? "" : reportMd);
        completion.setReportFileUrl(reportFileUrl);
        completion.setReportFileName(reportFileUrl == null ? null : blank(request.reportFileName()));
        completion.setPrUrls(String.join("\n", urls));
        completion.setStatus("SUBMITTED");
        completion.setReviewNote(null);
        completions.save(completion);
        project.setStatus("COMPLETING");
        String auditDetail = reportFileUrl != null ? reportFileUrl : "TEXT";
        audit.record(student.getId(), "COMPLETION", completion.getId(), "SUBMIT", auditDetail);
        return toResponse(project);
    }

    @Transactional
    public ProjectResponse reviewCompletion(Long projectId, ReviewRequest request) {
        AuthUser publisher = AuthSupport.requireRole(Role.PUBLISHER);
        Project project = visible(projectId);
        if (!project.getPublisher().getId().equals(publisher.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能审核自己的项目");
        }
        Completion completion = completions.findByProjectId(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "还没有结项材料"));
        if (!"SUBMITTED".equals(completion.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "结项材料不在待审状态");
        }
        completion.setReviewNote(blank(request.note()));
        completion.setReviewedAt(LocalDateTime.now());
        if ("APPROVE".equals(request.decision())) {
            completion.setStatus("APPROVED");
            project.setStatus("COMPLETED");
            points.award(
                    project.getStudent().getId(),
                    50,
                    "结项通过：「" + project.getTask().getTitle() + "」",
                    "COMPLETION_APPROVE",
                    String.valueOf(projectId),
                    "COMPLETION_APPROVE:" + projectId);
            audit.record(publisher.getId(), "COMPLETION", completion.getId(), "APPROVE", null);
        } else if ("REJECT".equals(request.decision())) {
            completion.setStatus("REJECTED");
            project.setStatus("ACTIVE");
            audit.record(publisher.getId(), "COMPLETION", completion.getId(), "REJECT", request.note());
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "决定只能是通过或驳回");
        }
        return toResponse(project);
    }

    private void advance(Long projectId, int approvedSeq) {
        milestones.findByProjectIdOrderBySeqAsc(projectId).stream()
                .filter(item -> item.getSeq() == approvedSeq + 1 && "PENDING".equals(item.getStatus()))
                .findFirst()
                .ifPresent(next -> next.setStatus("IN_PROGRESS"));
    }

    private Project visible(Long id) {
        AuthUser user = AuthSupport.current();
        Project project = projects.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "项目不存在"));
        boolean allowed = user.getRole() == Role.ADMIN
                || project.getStudent().getId().equals(user.getId())
                || project.getPublisher().getId().equals(user.getId());
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查看该项目");
        }
        return project;
    }

    private Milestone milestone(Long projectId, Long milestoneId) {
        Milestone milestone = milestones.findById(milestoneId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "节点不存在"));
        if (!milestone.getProject().getId().equals(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "节点不存在");
        }
        if (milestone.getSeq() < 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "需求与方案已在申请阶段确认，无需重复提交或评审");
        }
        return milestone;
    }

    private ProjectResponse toResponse(Project project) {
        List<MilestoneView> milestoneViews = milestones.findByProjectIdOrderBySeqAsc(project.getId()).stream()
                .filter(item -> item.getSeq() >= 3)
                .map(item -> {
                    MilestoneTemplate template = templateFor(item.getSeq(), item.getTitle());
                    return new MilestoneView(
                            item.getId(),
                            item.getSeq(),
                            template.title(),
                            template.goal(),
                            template.evidence(),
                            item.getStatus(),
                            item.getNote(),
                            item.getDeliverableUrl());
                })
                .toList();
        List<ReportView> reportViews = reports.findByProjectIdOrderByWeekIndexAsc(project.getId()).stream()
                .map(item -> new ReportView(
                        item.getId(),
                        item.getWeekIndex(),
                        item.getContent(),
                        item.getBlockers(),
                        item.getPlan(),
                        item.getSupportNeeded(),
                        item.getStatus(),
                        item.getScore(),
                        item.getScoreComment(),
                        item.getCreatedAt()))
                .toList();
        CompletionView completionView = completions.findByProjectId(project.getId())
                .map(item -> new CompletionView(
                        item.getId(),
                        item.getReportMd(),
                        item.getReportFileUrl(),
                        item.getReportFileName(),
                        Arrays.stream(item.getPrUrls().split("\n")).filter(url -> !url.isBlank()).toList(),
                        item.getStatus(),
                        item.getReviewNote()))
                .orElse(null);
        var application = project.getApplication();
        return new ProjectResponse(
                project.getId(),
                project.getTask().getId(),
                project.getTask().getTitle(),
                project.getTask().getTechField(),
                project.getTask().getRoleType(),
                project.getTask().getPostingMode(),
                project.getStudent().getId(),
                project.getStudent().getDisplayName(),
                project.getStudent().getAbilityLevel(),
                project.getPublisher().getId(),
                project.getPublisher().getRealName() == null ? project.getPublisher().getDisplayName() : project.getPublisher().getRealName(),
                project.getStatus(),
                project.getEnrolledAt(),
                application.getProposalMd(),
                application.getId(),
                application.getProposalFileName(),
                application.getAiScore(),
                application.getAiSummary(),
                application.getAiDetailJson(),
                application.getAiStatus(),
                application.getAiScoredAt(),
                milestoneViews,
                reportViews,
                completionView);
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record ProjectResponse(
            Long id,
            Long taskId,
            String taskTitle,
            String taskType,
            String taskRoleType,
            String postingMode,
            Long studentId,
            String studentName,
            String abilityLevel,
            Long publisherId,
            String publisherName,
            String status,
            LocalDateTime enrolledAt,
            String proposalMd,
            Long applicationId,
            String proposalFileName,
            Integer aiScore,
            String aiSummary,
            String aiDetailJson,
            String aiStatus,
            LocalDateTime aiScoredAt,
            List<MilestoneView> milestones,
            List<ReportView> reports,
            CompletionView completion
    ) {
    }

    public record MilestoneView(
            Long id,
            Integer seq,
            String title,
            String goal,
            String evidence,
            String status,
            String note,
            String deliverableUrl
    ) {
    }

    private record MilestoneTemplate(String title, String goal, String evidence) {
    }

    private MilestoneTemplate templateFor(int seq, String title) {
        return seq == 3 ? DELIVERY
                : new MilestoneTemplate(title, "完成本阶段约定工作。", "提交：阶段说明与交付链接");
    }

    public record ReportView(
            Long id,
            Integer weekIndex,
            String content,
            String blockers,
            String plan,
            String supportNeeded,
            String status,
            Integer score,
            String scoreComment,
            LocalDateTime createdAt
    ) {
    }

    public record CompletionView(
            Long id,
            String reportMd,
            String reportFileUrl,
            String reportFileName,
            List<String> prUrls,
            String status,
            String reviewNote
    ) {
    }

    public record MilestoneSubmit(@NotBlank(message = "请填写节点说明") @Size(max = 1000) String note, @Size(max = 500) String deliverableUrl) {
    }

    public record ReviewRequest(@NotBlank String decision, @Size(max = 500) String note) {
    }

    public record ReportRequest(
            @NotBlank(message = "请填写本周完成") String content,
            String blockers,
            String plan,
            String supportNeeded
    ) {
    }

    public record ScoreRequest(@Min(1) @Max(5) Integer score, @Size(max = 500) String comment) {
    }

    public record CompletionRequest(
            String reportMd,
            @Size(max = 500) String reportFileUrl,
            @Size(max = 255) String reportFileName,
            List<@NotBlank String> prUrls
    ) {
    }
}

