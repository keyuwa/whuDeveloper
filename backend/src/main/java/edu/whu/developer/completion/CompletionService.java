package edu.whu.developer.completion;

import edu.whu.developer.auth.Role;
import edu.whu.developer.common.AuthSupport;
import edu.whu.developer.common.AuthUser;
import edu.whu.developer.project.Project;
import edu.whu.developer.project.ProjectGuard;
import edu.whu.developer.project.ProjectStatus;
import edu.whu.developer.report.WeeklyReportRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
public class CompletionService {

    private final CompletionRepository completions;
    private final WeeklyReportRepository reports;
    private final ProjectGuard guard;

    public CompletionService(CompletionRepository completions, WeeklyReportRepository reports, ProjectGuard guard) {
        this.completions = completions;
        this.reports = reports;
        this.guard = guard;
    }

    @Transactional(readOnly = true)
    public Optional<CompletionResponse> find(Long projectId) {
        Project project = guard.requireVisible(projectId);
        return completions.findByProjectId(project.getId()).map(CompletionResponse::from);
    }

    @Transactional
    public CompletionResponse complete(Long projectId, CompletionRequest request) {
        AuthUser student = AuthSupport.requireRole(Role.STUDENT);
        Project project = guard.requireVisible(projectId);
        if (!project.getStudent().getId().equals(student.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有入学学生可以结项");
        }
        if (completions.findByProjectId(projectId).isPresent() || project.getStatus() == ProjectStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "项目已经结项");
        }
        if (!reports.existsByProjectId(projectId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先提交至少一份周报");
        }
        Completion completion = new Completion();
        completion.setProject(project);
        completion.setPrUrl(request.prUrl().trim());
        completion.setSummary(request.summary().trim());
        completions.save(completion);
        project.setStatus(ProjectStatus.COMPLETED);
        return CompletionResponse.from(completion);
    }
}
