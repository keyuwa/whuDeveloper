package edu.whu.developer.application;

import edu.whu.developer.project.ProjectResponse;
import edu.whu.developer.project.ProjectService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ApplicationController {

    private final ApplicationService applications;
    private final ProjectService projects;

    public ApplicationController(ApplicationService applications, ProjectService projects) {
        this.applications = applications;
        this.projects = projects;
    }

    @PostMapping("/tasks/{taskId}/applications")
    public ApplicationResponse submit(@PathVariable Long taskId, @Valid @RequestBody ProposalRequest request) {
        return applications.submit(taskId, request);
    }

    @GetMapping("/tasks/{taskId}/applications")
    public List<ApplicationResponse> listForTask(@PathVariable Long taskId) {
        return applications.listForTask(taskId);
    }

    @GetMapping("/applications/mine")
    public List<ApplicationResponse> mine() {
        return applications.mine();
    }

    @PostMapping("/applications/{id}/accept")
    public ApplicationResponse accept(@PathVariable Long id) {
        return applications.accept(id);
    }

    @PostMapping("/applications/{id}/enroll")
    public ProjectResponse enroll(@PathVariable Long id) {
        return projects.enroll(id);
    }
}
