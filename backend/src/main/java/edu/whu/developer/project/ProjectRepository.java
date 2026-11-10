package edu.whu.developer.project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByApplicationId(Long applicationId);

    List<Project> findByStudentIdOrderByIdDesc(Long studentId);

    List<Project> findByPublisherIdOrderByIdDesc(Long publisherId);

    long countByTaskIdAndStatusNot(Long taskId, String status);

    boolean existsByTaskId(Long taskId);

    List<Project> findByTaskId(Long taskId);

    @Query("""
            SELECT p.task.id, COUNT(p) FROM Project p
            WHERE p.task.id IN :taskIds AND p.status <> :excludedStatus
            GROUP BY p.task.id
            """)
    List<Object[]> countGroupedByTaskIdExcludingStatus(
            @Param("taskIds") Collection<Long> taskIds,
            @Param("excludedStatus") String excludedStatus
    );
}
