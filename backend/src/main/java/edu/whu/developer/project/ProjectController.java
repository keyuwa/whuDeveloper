package edu.whu.developer.project;

import edu.whu.developer.project.ProjectService.CompletionRequest;
import edu.whu.developer.project.ProjectService.MilestoneSubmit;
import edu.whu.developer.project.ProjectService.ProjectResponse;
import edu.whu.developer.project.ProjectService.ReportRequest;
import edu.whu.developer.project.ProjectService.ReviewRequest;
import edu.whu.developer.project.ProjectService.ScoreRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projects;

    public ProjectController(ProjectService projects) {
        this.projects = projects;
    }

    @GetMapping
    public List<ProjectResponse> mine() {
        return projects.mine();
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@PathVariable Long id) {
        return projects.get(id);
    }

    @PostMapping("/{id}/milestones/{milestoneId}/submit")
    public ProjectResponse submitMilestone(@PathVariable Long id, @PathVariable Long milestoneId, @Valid @RequestBody MilestoneSubmit request) {
        return projects.submitMilestone(id, milestoneId, request);
    }

    @PostMapping("/{id}/milestones/{milestoneId}/review")
    public ProjectResponse reviewMilestone(@PathVariable Long id, @PathVariable Long milestoneId, @Valid @RequestBody ReviewRequest request) {
        return projects.reviewMilestone(id, milestoneId, request);
    }

    @PostMapping("/{id}/reports")
    public ProjectResponse submitReport(@PathVariable Long id, @Valid @RequestBody ReportRequest request) {
        return projects.submitReport(id, request);
    }

    @PostMapping("/{id}/reports/{reportId}/score")
    public ProjectResponse score(@PathVariable Long id, @PathVariable Long reportId, @Valid @RequestBody ScoreRequest request) {
        return projects.scoreReport(id, reportId, request);
    }

    @PostMapping("/{id}/completion")
    public ProjectResponse complete(@PathVariable Long id, @Valid @RequestBody CompletionRequest request) {
        return projects.submitCompletion(id, request);
    }

    @PostMapping("/{id}/completion/review")
    public ProjectResponse reviewCompletion(@PathVariable Long id, @Valid @RequestBody ReviewRequest request) {
        return projects.reviewCompletion(id, request);
    }
}
