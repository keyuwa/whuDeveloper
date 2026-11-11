package edu.whu.developer.application;

import edu.whu.developer.application.ApplicationService.ApplicationResponse;
import edu.whu.developer.application.ApplicationService.AwardRequest;
import edu.whu.developer.application.ApplicationService.InterviewRequest;
import edu.whu.developer.application.ApplicationService.ReviewRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ApplicationController {

    private final ApplicationService applications;

    public ApplicationController(ApplicationService applications) {
        this.applications = applications;
    }

    @PostMapping(value = "/tasks/{taskId}/applications", consumes = "multipart/form-data")
    public ApplicationResponse submit(
            @PathVariable Long taskId,
            @org.springframework.web.bind.annotation.RequestParam(value = "proposalText", required = false) String proposalText,
            @org.springframework.web.bind.annotation.RequestParam(value = "positionId", required = false) Long positionId,
            @org.springframework.web.bind.annotation.RequestParam(value = "file", required = false) org.springframework.web.multipart.MultipartFile file) {
        return applications.submit(taskId, proposalText, positionId, file);
    }

    @GetMapping("/tasks/{taskId}/applications")
    public List<ApplicationResponse> forTask(@PathVariable Long taskId) {
        return applications.forTask(taskId);
    }

    @GetMapping(value = "/applications/{id}/proposal", produces = "application/pdf")
    public org.springframework.http.ResponseEntity<byte[]> proposal(@PathVariable Long id) {
        return org.springframework.http.ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .header("Content-Disposition", "attachment; filename=proposal.pdf")
                .header("X-Content-Type-Options", "nosniff")
                .body(applications.proposal(id));
    }

    @GetMapping("/me/applications")
    public List<ApplicationResponse> mine() {
        return applications.mine();
    }

    @GetMapping("/publisher/applications")
    public List<ApplicationResponse> inbox() {
        return applications.publisherInbox();
    }

    @GetMapping("/publisher/applications/pending-count")
    public Map<String, Long> pendingCount() {
        return Map.of("count", applications.publisherPendingCount());
    }

    @PostMapping("/applications/{id}/review")
    public ApplicationResponse review(@PathVariable Long id, @Valid @RequestBody ReviewRequest request) {
        return applications.review(id, request);
    }

    @PostMapping("/applications/{id}/award")
    public ApplicationResponse award(@PathVariable Long id, @RequestBody(required = false) AwardRequest request) {
        return applications.award(id, request == null ? new AwardRequest(null) : request);
    }

    @PostMapping("/applications/{id}/interview")
    public ApplicationResponse interview(@PathVariable Long id, @Valid @RequestBody InterviewRequest request) {
        return applications.interview(id, request);
    }

    @PostMapping("/applications/{id}/ai-rescore")
    public ApplicationResponse aiRescore(@PathVariable Long id) {
        return applications.rescoreAi(id);
    }
}
