package edu.whu.developer.task;

import edu.whu.developer.application.ApplicationStatus;
import edu.whu.developer.application.TaskApplication;
import edu.whu.developer.application.TaskApplicationRepository;
import edu.whu.developer.auth.Role;
import edu.whu.developer.common.AuthSupport;
import edu.whu.developer.common.AuthUser;
import edu.whu.developer.project.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class TaskService {

    private final TaskRepository tasks;
    private final TaskApplicationRepository applications;
    private final ProjectRepository projects;

    public TaskService(TaskRepository tasks, TaskApplicationRepository applications, ProjectRepository projects) {
        this.tasks = tasks;
        this.applications = applications;
        this.projects = projects;
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> list() {
        AuthUser user = AuthSupport.current();
        return tasks.findAllByOrderByIdAsc().stream()
                .map(task -> toResponse(task, user))
                .toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse get(Long id) {
        AuthUser user = AuthSupport.current();
        Task task = tasks.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        return toResponse(task, user);
    }

    private TaskResponse toResponse(Task task, AuthUser user) {
        TaskResponse.ApplicationBrief brief = null;
        if (user.getRole() == Role.STUDENT) {
            brief = applications.findByTaskIdAndStudentId(task.getId(), user.getId())
                    .map(application -> new TaskResponse.ApplicationBrief(
                            application.getId(),
                            application.getProposal(),
                            application.getStatus().name(),
                            projectId(application)))
                    .orElse(null);
        }
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getType().name(),
                task.getStatus().name(),
                task.getPublisher().getId(),
                task.getPublisher().getDisplayName(),
                task.getCreatedAt(),
                brief);
    }

    private Long projectId(TaskApplication application) {
        if (application.getStatus() != ApplicationStatus.ACCEPTED) {
            return null;
        }
        return projects.findByApplicationId(application.getId())
                .map(project -> project.getId())
                .orElse(null);
    }
}
