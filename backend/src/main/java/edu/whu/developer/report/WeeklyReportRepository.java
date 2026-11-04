package edu.whu.developer.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WeeklyReportRepository extends JpaRepository<WeeklyReport, Long> {

    List<WeeklyReport> findByProjectIdOrderByWeekIndexAsc(Long projectId);

    Optional<WeeklyReport> findByProjectIdAndWeekIndex(Long projectId, Integer weekIndex);

    void deleteByProjectId(Long projectId);
}
