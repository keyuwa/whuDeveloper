package edu.whu.developer.project;

import edu.whu.developer.application.TaskApplication;
import edu.whu.developer.auth.Role;
import edu.whu.developer.common.AuthSupport;
import edu.whu.developer.common.AuthUser;
import edu.whu.developer.completion.Completion;
import edu.whu.developer.completion.CompletionRepository;
import edu.whu.developer.notify.AuditService;
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

    private static final List<String> MILESTONES = List.of("对齐启动", "迭代开发", "结项验收");

    private final ProjectRepository projects;
    private final MilestoneRepository milestones;
    private final WeeklyReportRepository reports;
    private final CompletionRepository completions;
    private final AuditService audit;

    public ProjectService(ProjectRepository projects,
                          MilestoneRepository milestones,
                          WeeklyReportRepository reports,
                          CompletionRepository completions,
                          AuditService audit) {
        this.projects = projects;
        this.milestones = milestones;
        this.reports = reports;
        this.completions = completions;
        this.audit = audit;
    }

    @Transactional
    public ProjectResponse openFromApplication(TaskApplication application) {
        return projects.findByApplicationId(application.getId())
                .map(this::toResponse)
                .orElseGet(() -> {
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
                    for (int i = 0; i < MILESTONES.size(); i++) {
                        Milestone milestone = new Milestone();
                        milestone.setProject(project);
                        milestone.setSeq(i + 1);
                        milestone.setTitle(MILESTONES.get(i));
                        milestone.setStatus(i == 0 ? "IN_PROGRESS" : "PENDING");
                        milestones.save(milestone);
                    }
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
    public ProjectResponse submitMilestone(Long projectId, Long milestoneId, MilestoneSubmit request) {
        AuthUser student = AuthSupport.requireRole(Role.STUDENT);
        Project project = visible(projectId);
        if (!project.getStudent().getId().equals(student.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有入学学生可以提交节点");
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
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有入学学生可以提交周报");
        }
        if ("COMPLETED".equals(project.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "项目已结项，不能再交周报");
        }
        WeeklyReport report = reports.findByProjectIdAndWeekIndex(projectId, request.weekIndex())
                .orElseGet(WeeklyReport::new);
        if (report.getId() != null && "SCORED".equals(report.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "这周的周报已经评分，不能修改");
        }
        report.setProject(project);
        report.setWeekIndex(request.weekIndex());
        report.setContent(request.content().trim());
        report.setPlan(blank(request.plan()));
        report.setStatus("SUBMITTED");
        reports.save(report);
        audit.record(student.getId(), "REPORT", report.getId(), "SUBMIT", "W" + request.weekIndex());
        return toResponse(project);
    }

    @Transactional
    public ProjectResponse scoreReport(Long projectId, Long reportId, ScoreRequest request) {
        AuthUser publisher = AuthSupport.requireRole(Role.PUBLISHER);
        Project project = visible(projectId);
        if (!project.getPublisher().getId().equals(publisher.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能给自己的项目评分");
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
        audit.record(publisher.getId(), "REPORT", report.getId(), "SCORE", String.valueOf(request.score()));
        return toResponse(project);
    }

    @Transactional
    public ProjectResponse submitCompletion(Long projectId, CompletionRequest request) {
        AuthUser student = AuthSupport.requireRole(Role.STUDENT);
        Project project = visible(projectId);
        if (!project.getStudent().getId().equals(student.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有入学学生可以提交结项");
        }
        if (reports.findByProjectIdOrderByWeekIndexAsc(projectId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先提交至少一份周报");
        }
        List<String> urls = request.prUrls().stream().map(String::trim).filter(url -> !url.isBlank()).toList();
        if (urls.isEmpty() || urls.stream().anyMatch(url -> !url.startsWith("http://") && !url.startsWith("https://"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请至少填写一条 http(s) PR 链接");
        }
        Completion completion = completions.findByProjectId(projectId).orElseGet(Completion::new);
        if ("APPROVED".equals(completion.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "结项已经通过");
        }
        completion.setProject(project);
        completion.setReportMd(request.reportMd().trim());
        completion.setPrUrls(String.join("\n", urls));
        completion.setStatus("SUBMITTED");
        completion.setReviewNote(null);
        completions.save(completion);
        project.setStatus("COMPLETING");
        audit.record(student.getId(), "COMPLETION", completion.getId(), "SUBMIT", urls.get(0));
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
        return milestone;
    }

    private ProjectResponse toResponse(Project project) {
        List<MilestoneView> milestoneViews = milestones.findByProjectIdOrderBySeqAsc(project.getId()).stream()
                .map(item -> new MilestoneView(item.getId(), item.getSeq(), item.getTitle(), item.getStatus(), item.getNote(), item.getDeliverableUrl()))
                .toList();
        List<ReportView> reportViews = reports.findByProjectIdOrderByWeekIndexAsc(project.getId()).stream()
                .map(item -> new ReportView(item.getId(), item.getWeekIndex(), item.getContent(), item.getPlan(), item.getStatus(), item.getScore(), item.getScoreComment()))
                .toList();
        CompletionView completionView = completions.findByProjectId(project.getId())
                .map(item -> new CompletionView(
                        item.getId(),
                        item.getReportMd(),
                        Arrays.stream(item.getPrUrls().split("\n")).filter(url -> !url.isBlank()).toList(),
                        item.getStatus(),
                        item.getReviewNote()))
                .orElse(null);
        return new ProjectResponse(
                project.getId(),
                project.getTask().getId(),
                project.getTask().getTitle(),
                project.getTask().getRoleType(),
                project.getStudent().getId(),
                project.getStudent().getDisplayName(),
                project.getStudent().getAbilityLevel(),
                project.getPublisher().getId(),
                project.getPublisher().getRealName() == null ? project.getPublisher().getDisplayName() : project.getPublisher().getRealName(),
                project.getStatus(),
                project.getEnrolledAt(),
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
            Long studentId,
            String studentName,
            String abilityLevel,
            Long publisherId,
            String publisherName,
            String status,
            LocalDateTime enrolledAt,
            List<MilestoneView> milestones,
            List<ReportView> reports,
            CompletionView completion
    ) {
    }

    public record MilestoneView(Long id, Integer seq, String title, String status, String note, String deliverableUrl) {
    }

    public record ReportView(Long id, Integer weekIndex, String content, String plan, String status, Integer score, String scoreComment) {
    }

    public record CompletionView(Long id, String reportMd, List<String> prUrls, String status, String reviewNote) {
    }

    public record MilestoneSubmit(@NotBlank(message = "请填写节点说明") @Size(max = 1000) String note, @Size(max = 500) String deliverableUrl) {
    }

    public record ReviewRequest(@NotBlank String decision, @Size(max = 500) String note) {
    }

    public record ReportRequest(
            @NotNull @Min(1) @Max(30) Integer weekIndex,
            @NotBlank(message = "请填写本周工作") String content,
            String plan
    ) {
    }

    public record ScoreRequest(@NotNull @Min(1) @Max(5) Integer score, @Size(max = 500) String comment) {
    }

    public record CompletionRequest(@NotBlank(message = "请填写结项报告") String reportMd, @NotNull List<@NotBlank String> prUrls) {
    }
}
