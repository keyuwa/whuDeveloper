package edu.whu.developer.application;

import edu.whu.developer.ability.PointService;
import edu.whu.developer.auth.Role;
import edu.whu.developer.auth.User;
import edu.whu.developer.auth.UserRepository;
import edu.whu.developer.auth.UserResponse;
import edu.whu.developer.common.AuthSupport;
import edu.whu.developer.common.AuthUser;
import edu.whu.developer.common.MailService;
import edu.whu.developer.notify.AuditService;
import edu.whu.developer.notify.NotificationService;
import edu.whu.developer.project.ProjectRepository;
import edu.whu.developer.project.ProjectService;
import edu.whu.developer.shortlist.TaskShortlistRepository;
import edu.whu.developer.task.Task;
import edu.whu.developer.task.TaskRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ApplicationService {

    private final TaskRepository tasks;
    private final TaskApplicationRepository applications;
    private final UserRepository users;
    private final ProjectRepository projects;
    private final ProjectService projectService;
    private final TaskShortlistRepository shortlists;
    private final NotificationService notifications;
    private final MailService mail;
    private final AuditService audit;
    private final PointService points;

    public ApplicationService(TaskRepository tasks,
                              TaskApplicationRepository applications,
                              UserRepository users,
                              ProjectRepository projects,
                              ProjectService projectService,
                              TaskShortlistRepository shortlists,
                              NotificationService notifications,
                              MailService mail,
                              AuditService audit,
                              PointService points) {
        this.tasks = tasks;
        this.applications = applications;
        this.users = users;
        this.projects = projects;
        this.projectService = projectService;
        this.shortlists = shortlists;
        this.points = points;
        this.notifications = notifications;
        this.mail = mail;
        this.audit = audit;
    }

    @Transactional
    public ApplicationResponse submit(Long taskId,
                                      String proposalText,
                                      org.springframework.web.multipart.MultipartFile file) {
        AuthUser student = AuthSupport.requireRole(Role.STUDENT);
        Task task = tasks.findById(taskId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        if (!"OPEN".equals(task.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "任务未开放");
        }
        if (applications.findByTaskIdAndStudentId(taskId, student.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "每个任务只能投递一次");
        }
        User studentEntity = users.findById(student.getId()).orElseThrow();
        if (!UserResponse.isResumeReady(studentEntity)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先完善简历后再投递申请");
        }
        String text = proposalText == null ? "" : proposalText.trim();
        boolean hasFile = file != null && !file.isEmpty();
        if (text.isBlank() && !hasFile) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写申请内容，或上传 PDF 附件");
        }
        if (text.length() > 10000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "申请文字不超过 10000 字");
        }
        TaskApplication application = new TaskApplication();
        application.setTask(task);
        application.setStudent(studentEntity);
        application.setProposalMd(text);
        if (hasFile) {
            byte[] pdf = readPdf(file);
            application.setProposalPdf(pdf);
            String filename = file.getOriginalFilename();
            application.setProposalFileName(filename == null
                    ? "proposal.pdf"
                    : filename.replace('\\', '/').substring(filename.replace('\\', '/').lastIndexOf('/') + 1));
        }
        application.setStatus("SUBMITTED");
        applications.save(application);
        shortlists.deleteByStudentIdAndTaskId(student.getId(), taskId);
        notifications.push(task.getPublisher().getId(), "新的申请书", student.getDisplayName() + " 申请了「" + task.getTitle() + "」");
        audit.record(student.getId(), "APPLICATION", application.getId(), "SUBMIT", task.getTitle());
        return toResponse(application);
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> mine() {
        AuthUser student = AuthSupport.requireRole(Role.STUDENT);
        return applications.findByStudentIdOrderByIdDesc(student.getId()).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> forTask(Long taskId) {
        AuthUser publisher = AuthSupport.requireRole(Role.PUBLISHER);
        Task task = tasks.findById(taskId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        if (!task.getPublisher().getId().equals(publisher.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能查看自己任务的申请");
        }
        return applications.findByTaskIdOrderByIdAsc(taskId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> publisherInbox() {
        AuthUser publisher = AuthSupport.requireRole(Role.PUBLISHER);
        return applications.findByTask_Publisher_IdOrderByIdDesc(publisher.getId()).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ApplicationResponse review(Long id, ReviewRequest request) {
        AuthUser publisher = AuthSupport.requireRole(Role.PUBLISHER);
        TaskApplication application = applications.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "申请不存在"));
        if (!application.getTask().getPublisher().getId().equals(publisher.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能审核自己任务的申请");
        }
        if (!"SUBMITTED".equals(application.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "这份申请已经处理过");
        }
        application.setReviewNote(blank(request.note()));
        if ("ACCEPT".equals(request.decision())) {
            application.setStatus("ACCEPTED");
            var project = projectService.openFromApplication(application);
            points.award(
                    application.getStudent().getId(),
                    20,
                    "录用入项：「" + application.getTask().getTitle() + "」",
                    "ENROLL",
                    String.valueOf(project.id()),
                    "ENROLL:" + project.id());
            notifyStudent(application, "申请已通过", "你申请的「" + application.getTask().getTitle() + "」已通过，可进入项目协作。");
            audit.record(publisher.getId(), "APPLICATION", application.getId(), "ACCEPT", null);
        } else if ("REJECT".equals(request.decision())) {
            application.setStatus("REJECTED");
            notifyStudent(application, "申请未通过", "你申请的「" + application.getTask().getTitle() + "」未通过。");
            audit.record(publisher.getId(), "APPLICATION", application.getId(), "REJECT", request.note());
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "决定只能是通过或拒绝");
        }
        return toResponse(application);
    }

    @Transactional
    public ApplicationResponse interview(Long id, InterviewRequest request) {
        AuthUser publisher = AuthSupport.requireRole(Role.PUBLISHER);
        TaskApplication application = applications.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "申请不存在"));
        if (!application.getTask().getPublisher().getId().equals(publisher.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能为自己的任务安排面试");
        }
        application.setInterviewAt(request.interviewAt());
        application.setInterviewNote(blank(request.note()));
        notifyStudent(application, "面试安排", "「" + application.getTask().getTitle() + "」安排了面试。");
        audit.record(publisher.getId(), "APPLICATION", application.getId(), "INTERVIEW", String.valueOf(request.interviewAt()));
        return toResponse(application);
    }

    private void notifyStudent(TaskApplication application, String title, String body) {
        notifications.push(application.getStudent().getId(), title, body);
        mail.send(application.getStudent().getEmail(), title, body);
    }

    private ApplicationResponse toResponse(TaskApplication application) {
        Long projectId = projects.findByApplicationId(application.getId()).map(project -> project.getId()).orElse(null);
        User student = application.getStudent();
        return new ApplicationResponse(
                application.getId(),
                application.getTask().getId(),
                application.getTask().getTitle(),
                application.getTask().getTechField(),
                student.getId(),
                student.getDisplayName(),
                student.getRealName(),
                student.getSchool(),
                student.getCollege(),
                student.getMajor(),
                student.getStudentIdentity(),
                student.getBio(),
                student.getResumeFileUrl(),
                student.getResumeFileName(),
                student.getAbilityLevel(),
                application.getProposalMd(),
                application.getProposalFileName(),
                application.getStatus(),
                application.getInterviewAt(),
                application.getInterviewNote(),
                application.getReviewNote(),
                application.getRemindCount(),
                application.getCreatedAt(),
                projectId);
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static byte[] readPdf(org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 5 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请上传不超过 5 MB 的 PDF 申请书");
        }
        String name = file.getOriginalFilename();
        if (name == null || name.length() > 255 || !name.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "申请书必须是 PDF 文件，文件名不超过 255 字符");
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length < 5 || !new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件不是有效的 PDF");
            }
            return bytes;
        } catch (java.io.IOException error) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "读取申请书失败，请重试", error);
        }
    }

    @Transactional(readOnly = true)
    public byte[] proposal(Long id) {
        AuthUser user = AuthSupport.current();
        TaskApplication application = applications.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "申请不存在"));
        if (user.getRole() != Role.ADMIN && !application.getStudent().getId().equals(user.getId())
                && !application.getTask().getPublisher().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查看该申请书");
        }
        if (application.getProposalFileName() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "该申请没有 PDF 文件");
        }
        return application.getProposalPdf();
    }

    public record ReviewRequest(@NotBlank String decision, @Size(max = 500) String note) {
    }

    public record InterviewRequest(LocalDateTime interviewAt, @Size(max = 500) String note) {
    }

    public record ApplicationResponse(
            Long id,
            Long taskId,
            String taskTitle,
            String taskType,
            Long studentId,
            String studentName,
            String realName,
            String school,
            String college,
            String major,
            String studentIdentity,
            String bio,
            String resumeFileUrl,
            String resumeFileName,
            String abilityLevel,
            String proposalMd,
            String proposalFileName,
            String status,
            LocalDateTime interviewAt,
            String interviewNote,
            String reviewNote,
            int remindCount,
            LocalDateTime createdAt,
            Long projectId
    ) {
    }
}
