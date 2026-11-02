package edu.whu.developer.report;

import edu.whu.developer.auth.Role;
import edu.whu.developer.common.AuthSupport;
import edu.whu.developer.common.AuthUser;
import edu.whu.developer.project.Project;
import edu.whu.developer.project.ProjectGuard;
import edu.whu.developer.project.ProjectStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ReportService {

    private final WeeklyReportRepository reports;
    private final ProjectGuard guard;

    public ReportService(WeeklyReportRepository reports, ProjectGuard guard) {
        this.reports = reports;
        this.guard = guard;
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> list(Long projectId) {
        Project project = guard.requireVisible(projectId);
        return reports.findByProjectIdOrderByWeekNumberAsc(project.getId()).stream()
                .map(ReportResponse::from)
                .toList();
    }

    @Transactional
    public ReportResponse submit(Long projectId, ReportRequest request) {
        AuthUser student = AuthSupport.requireRole(Role.STUDENT);
        Project project = guard.requireVisible(projectId);
        if (!project.getStudent().getId().equals(student.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有入学学生可以提交周报");
        }
        if (project.getStatus() == ProjectStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "项目已结项，不能再提交周报");
        }
        if (reports.existsByProjectIdAndWeekNumber(projectId, request.weekNumber())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "这一周的周报已经提交过了");
        }
        WeeklyReport report = new WeeklyReport();
        report.setProject(project);
        report.setWeekNumber(request.weekNumber());
        report.setContent(request.content().trim());
        reports.save(report);
        if (project.getStatus() == ProjectStatus.ENROLLED) {
            project.setStatus(ProjectStatus.IN_PROGRESS);
        }
        return ReportResponse.from(report);
    }
}
