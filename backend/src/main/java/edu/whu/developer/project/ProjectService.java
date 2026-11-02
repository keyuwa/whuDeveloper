package edu.whu.developer.project;

import edu.whu.developer.application.ApplicationStatus;
import edu.whu.developer.application.TaskApplication;
import edu.whu.developer.application.TaskApplicationRepository;
import edu.whu.developer.auth.Role;
import edu.whu.developer.common.AuthSupport;
import edu.whu.developer.common.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final TaskApplicationRepository applications;
    private final ProjectGuard guard;

    public ProjectService(ProjectRepository projects, TaskApplicationRepository applications, ProjectGuard guard) {
        this.projects = projects;
        this.applications = applications;
        this.guard = guard;
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list() {
        AuthUser user = AuthSupport.current();
        return projects.findByStudentIdOrPublisherIdOrderByIdDesc(user.getId(), user.getId()).stream()
                .map(ProjectResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(Long id) {
        return ProjectResponse.from(guard.requireVisible(id));
    }

    @Transactional
    public ProjectResponse enroll(Long applicationId) {
        AuthUser student = AuthSupport.requireRole(Role.STUDENT);
        TaskApplication application = applications.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "申请不存在"));
        if (!application.getStudent().getId().equals(student.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能入学自己的申请");
        }
        if (application.getStatus() != ApplicationStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "发布者录取后才能入学");
        }
        return projects.findByApplicationId(applicationId)
                .map(ProjectResponse::from)
                .orElseGet(() -> {
                    Project project = new Project();
                    project.setApplication(application);
                    project.setTask(application.getTask());
                    project.setStudent(application.getStudent());
                    project.setPublisher(application.getTask().getPublisher());
                    project.setStatus(ProjectStatus.ENROLLED);
                    projects.save(project);
                    return ProjectResponse.from(project);
                });
    }
}
