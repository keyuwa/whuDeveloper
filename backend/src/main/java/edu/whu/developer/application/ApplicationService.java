package edu.whu.developer.application;

import edu.whu.developer.auth.Role;
import edu.whu.developer.auth.User;
import edu.whu.developer.auth.UserRepository;
import edu.whu.developer.common.AuthSupport;
import edu.whu.developer.common.AuthUser;
import edu.whu.developer.project.ProjectRepository;
import edu.whu.developer.task.Task;
import edu.whu.developer.task.TaskRepository;
import edu.whu.developer.task.TaskStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ApplicationService {

    private final TaskRepository tasks;
    private final TaskApplicationRepository applications;
    private final ProjectRepository projects;
    private final UserRepository users;

    public ApplicationService(TaskRepository tasks,
                              TaskApplicationRepository applications,
                              ProjectRepository projects,
                              UserRepository users) {
        this.tasks = tasks;
        this.applications = applications;
        this.projects = projects;
        this.users = users;
    }

    @Transactional
    public ApplicationResponse submit(Long taskId, ProposalRequest request) {
        AuthUser student = AuthSupport.requireRole(Role.STUDENT);
        Task task = tasks.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        if (task.getStatus() != TaskStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "任务已关闭，不能申请");
        }
        if (applications.findByTaskIdAndStudentId(taskId, student.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "你已经申请过这个任务");
        }
        User studentEntity = users.findById(student.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录"));
        TaskApplication application = new TaskApplication();
        application.setTask(task);
        application.setStudent(studentEntity);
        application.setProposal(request.proposal().trim());
        application.setStatus(ApplicationStatus.SUBMITTED);
        applications.save(application);
        return ApplicationResponse.from(application, null);
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> listForTask(Long taskId) {
        AuthUser publisher = AuthSupport.requireRole(Role.PUBLISHER);
        Task task = tasks.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        if (!task.getPublisher().getId().equals(publisher.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能查看自己任务的申请");
        }
        return applications.findByTaskIdOrderByIdAsc(taskId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> mine() {
        AuthUser student = AuthSupport.requireRole(Role.STUDENT);
        return applications.findByStudentIdOrderByIdDesc(student.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ApplicationResponse accept(Long id) {
        AuthUser publisher = AuthSupport.requireRole(Role.PUBLISHER);
        TaskApplication application = applications.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "申请不存在"));
        if (!application.getTask().getPublisher().getId().equals(publisher.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能录取自己发布的任务");
        }
        if (application.getStatus() != ApplicationStatus.ACCEPTED) {
            application.setStatus(ApplicationStatus.ACCEPTED);
        }
        return toResponse(application);
    }

    private ApplicationResponse toResponse(TaskApplication application) {
        Long projectId = projects.findByApplicationId(application.getId())
                .map(project -> project.getId())
                .orElse(null);
        return ApplicationResponse.from(application, projectId);
    }
}
