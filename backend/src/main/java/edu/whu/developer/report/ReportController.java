package edu.whu.developer.report;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/reports")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping
    public List<ReportResponse> list(@PathVariable Long projectId) {
        return reports.list(projectId);
    }

    @PostMapping
    public ReportResponse create(@PathVariable Long projectId, @Valid @RequestBody ReportRequest request) {
        return reports.submit(projectId, request);
    }
}
