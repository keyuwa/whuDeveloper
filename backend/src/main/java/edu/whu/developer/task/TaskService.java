package edu.whu.developer.task;

import edu.whu.developer.application.TaskApplicationRepository;
import edu.whu.developer.auth.Role;
import edu.whu.developer.auth.User;
import edu.whu.developer.auth.UserRepository;
import edu.whu.developer.common.AuthSupport;
import edu.whu.developer.common.AuthUser;
import edu.whu.developer.notify.AuditService;
import edu.whu.developer.project.ProjectRepository;
import edu.whu.developer.task.TaskDtos.TaskRequest;
import edu.whu.developer.task.TaskDtos.TaskResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

@Service
public class TaskService {

    private static final Set<String> TYPES = Set.of("TA", "INTERN", "RA");
    private static final Set<String> LEVELS = Set.of("L1", "L2", "L3", "L4", "L5");

    private final TaskRepository tasks;
    private final UserRepository users;
    private final TaskApplicationRepository applications;
    private final ProjectRepository projects;
    private final AuditService audit;

    public TaskService(TaskRepository tasks,
                       UserRepository users,
                       TaskApplicationRepository applications,
                       ProjectRepository projects,
                       AuditService audit) {
        this.tasks = tasks;
        this.users = users;
        this.applications = applications;
        this.projects = projects;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> listOpen() {
        return tasks.findByStatusOrderByIdDesc("OPEN").stream().map(task -> toResponse(task, currentOrNull())).toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse get(Long id) {
        Task task = tasks.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        AuthUser viewer = currentOrNull();
        if (!"OPEN".equals(task.getStatus()) && !canManage(task, viewer)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
        }
        return toResponse(task, viewer);
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> mine() {
        AuthUser publisher = AuthSupport.requireRole(Role.PUBLISHER);
        return tasks.findByPublisherIdOrderByIdDesc(publisher.getId()).stream()
                .map(task -> toResponse(task, publisher))
                .toList();
    }

    @Transactional
    public TaskResponse create(TaskRequest request, boolean publish) {
        AuthUser publisher = AuthSupport.requireRole(Role.PUBLISHER);
        User owner = users.findById(publisher.getId()).orElseThrow();
        Task task = new Task();
        task.setPublisher(owner);
        apply(task, request);
        task.setStatus(publish ? "OPEN" : "DRAFT");
        tasks.save(task);
        audit.record(publisher.getId(), "TASK", task.getId(), publish ? "PUBLISH" : "CREATE", task.getTitle());
        return toResponse(task, publisher);
    }

    @Transactional
    public TaskResponse update(Long id, TaskRequest request) {
        Task task = owned(id);
        apply(task, request);
        audit.record(AuthSupport.current().getId(), "TASK", task.getId(), "UPDATE", task.getTitle());
        return toResponse(task, AuthSupport.current());
    }

    @Transactional
    public TaskResponse publish(Long id) {
        Task task = owned(id);
        task.setStatus("OPEN");
        audit.record(AuthSupport.current().getId(), "TASK", task.getId(), "PUBLISH", task.getTitle());
        return toResponse(task, AuthSupport.current());
    }

    @Transactional
    public TaskResponse close(Long id) {
        Task task = owned(id);
        task.setStatus("CLOSED");
        audit.record(AuthSupport.current().getId(), "TASK", task.getId(), "CLOSE", task.getTitle());
        return toResponse(task, AuthSupport.current());
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> allForAdmin() {
        AuthSupport.requireRole(Role.ADMIN);
        return tasks.findAll().stream().map(task -> toResponse(task, AuthSupport.current())).toList();
    }

    @Transactional
    public TaskResponse adminStatus(Long id, String status) {
        AuthUser admin = AuthSupport.requireRole(Role.ADMIN);
        if (!Set.of("DRAFT", "OPEN", "CLOSED").contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "状态不合法");
        }
        Task task = tasks.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        task.setStatus(status);
        audit.record(admin.getId(), "TASK", task.getId(), "ADMIN_STATUS", status);
        return toResponse(task, admin);
    }

    private Task owned(Long id) {
        AuthUser publisher = AuthSupport.requireRole(Role.PUBLISHER);
        Task task = tasks.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        if (!task.getPublisher().getId().equals(publisher.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能管理自己发布的任务");
        }
        return task;
    }

    private void apply(Task task, TaskRequest request) {
        String type = request.roleType().trim().toUpperCase();
        String level = request.difficulty().trim().toUpperCase();
        if (!TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "类型只能是 TA、INTERN 或 RA");
        }
        if (!LEVELS.contains(level)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "难度只能是 L1 到 L5");
        }
        task.setTitle(request.title().trim());
        task.setRoleType(type);
        task.setDifficulty(level);
        task.setBackground(request.background().trim());
        task.setTechStack(request.techStack().trim());
        task.setOutputRequirement(request.outputRequirement().trim());
        task.setTechRequirement(request.techRequirement().trim());
        task.setDurationWeeks(request.durationWeeks());
        task.setEstimatedHours(request.estimatedHours());
        task.setRepoUrl(blankToNull(request.repoUrl()));
        task.setLearningMaterials(blankToNull(request.learningMaterials()));
        task.setStipendText(request.stipendText().trim());
        task.setPrdMd(request.prdMd().trim());
        task.setTechDocMd(blankToNull(request.techDocMd()));
        task.setQuota(request.quota());
    }

    private TaskResponse toResponse(Task task, AuthUser viewer) {
        Long applicationId = null;
        String applicationStatus = null;
        Long projectId = null;
        if (viewer != null && viewer.getRole() == Role.STUDENT) {
            var application = applications.findByTaskIdAndStudentId(task.getId(), viewer.getId());
            if (application.isPresent()) {
                applicationId = application.get().getId();
                applicationStatus = application.get().getStatus();
                projectId = projects.findByApplicationId(applicationId).map(project -> project.getId()).orElse(null);
            }
        }
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getRoleType(),
                task.getDifficulty(),
                task.getBackground(),
                task.getTechStack(),
                task.getOutputRequirement(),
                task.getTechRequirement(),
                task.getDurationWeeks(),
                task.getEstimatedHours(),
                task.getRepoUrl(),
                task.getLearningMaterials(),
                task.getStipendText(),
                task.getPrdMd(),
                task.getTechDocMd(),
                task.getQuota(),
                projects.countByTaskIdAndStatusNot(task.getId(), "TERMINATED"),
                task.getStatus(),
                task.getPublisher().getId(),
                task.getPublisher().getRealName() == null ? task.getPublisher().getDisplayName() : task.getPublisher().getRealName(),
                task.getCreatedAt(),
                applicationId,
                applicationStatus,
                projectId);
    }

    private boolean canManage(Task task, AuthUser viewer) {
        if (viewer == null) {
            return false;
        }
        return viewer.getRole() == Role.ADMIN || task.getPublisher().getId().equals(viewer.getId());
    }

    private AuthUser currentOrNull() {
        try {
            return AuthSupport.current();
        } catch (ResponseStatusException ex) {
            return null;
        }
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
