package edu.whu.developer.application;

import edu.whu.developer.application.ApplicationService.ApplicationResponse;
import edu.whu.developer.application.ApplicationService.InterviewRequest;
import edu.whu.developer.application.ApplicationService.ProposalRequest;
import edu.whu.developer.application.ApplicationService.ReviewRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ApplicationController {

    private final ApplicationService applications;

    public ApplicationController(ApplicationService applications) {
        this.applications = applications;
    }

    @PostMapping("/tasks/{taskId}/applications")
    public ApplicationResponse submit(@PathVariable Long taskId, @Valid @RequestBody ProposalRequest request) {
        return applications.submit(taskId, request);
    }

    @GetMapping("/tasks/{taskId}/applications")
    public List<ApplicationResponse> forTask(@PathVariable Long taskId) {
        return applications.forTask(taskId);
    }

    @GetMapping("/me/applications")
    public List<ApplicationResponse> mine() {
        return applications.mine();
    }

    @GetMapping("/publisher/applications")
    public List<ApplicationResponse> inbox() {
        return applications.publisherInbox();
    }

    @PostMapping("/applications/{id}/review")
    public ApplicationResponse review(@PathVariable Long id, @Valid @RequestBody ReviewRequest request) {
        return applications.review(id, request);
    }

    @PostMapping("/applications/{id}/interview")
    public ApplicationResponse interview(@PathVariable Long id, @Valid @RequestBody InterviewRequest request) {
        return applications.interview(id, request);
    }
}
