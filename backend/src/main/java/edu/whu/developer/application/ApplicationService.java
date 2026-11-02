package edu.whu.developer.application;

import edu.whu.developer.auth.Role;
import edu.whu.developer.auth.User;
import edu.whu.developer.auth.UserRepository;
import edu.whu.developer.common.AuthSupport;
import edu.whu.developer.common.AuthUser;
import edu.whu.developer.common.MailService;
import edu.whu.developer.notify.AuditService;
import edu.whu.developer.notify.NotificationService;
import edu.whu.developer.project.ProjectRepository;
import edu.whu.developer.project.ProjectService;
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
    private final NotificationService notifications;
    private final MailService mail;
    private final AuditService audit;

    public ApplicationService(TaskRepository tasks,
                              TaskApplicationRepository applications,
                              UserRepository users,
                              ProjectRepository projects,
                              ProjectService projectService,
                              NotificationService notifications,
                              MailService mail,
                              AuditService audit) {
        this.tasks = tasks;
        this.applications = applications;
        this.users = users;
        this.projects = projects;
        this.projectService = projectService;
        this.notifications = notifications;
        this.mail = mail;
        this.audit = audit;
    }

    @Transactional
    public ApplicationResponse submit(Long taskId, ProposalRequest request) {
        AuthUser student = AuthSupport.requireRole(Role.STUDENT);
        Task task = tasks.findById(taskId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        if (!"OPEN".equals(task.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "任务未开放");
        }
        if (applications.findByTaskIdAndStudentId(taskId, student.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "每个任务只能投递一次");
        }
        User studentEntity = users.findById(student.getId()).orElseThrow();
        TaskApplication application = new TaskApplication();
        application.setTask(task);
        application.setStudent(studentEntity);
        application.setProposalMd(request.proposalMd().trim());
        application.setStatus("SUBMITTED");
        applications.save(application);
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
            projectService.openFromApplication(application);
            notifyStudent(application, "申请已通过", "你申请的「" + application.getTask().getTitle() + "」已录取并入学。");
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
        return new ApplicationResponse(
                application.getId(),
                application.getTask().getId(),
                application.getTask().getTitle(),
                application.getTask().getRoleType(),
                application.getStudent().getId(),
                application.getStudent().getDisplayName(),
                application.getStudent().getAbilityLevel(),
                application.getProposalMd(),
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

    public record ProposalRequest(@NotBlank(message = "请填写申请书") String proposalMd) {
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
            String abilityLevel,
            String proposalMd,
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
