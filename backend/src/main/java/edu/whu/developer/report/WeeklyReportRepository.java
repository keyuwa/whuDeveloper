package edu.whu.developer.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WeeklyReportRepository extends JpaRepository<WeeklyReport, Long> {

    List<WeeklyReport> findByProjectIdOrderByWeekNumberAsc(Long projectId);

    boolean existsByProjectId(Long projectId);

    boolean existsByProjectIdAndWeekNumber(Long projectId, Integer weekNumber);
}
