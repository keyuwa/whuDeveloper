package edu.whu.developer.application;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TaskApplicationRepository extends JpaRepository<TaskApplication, Long> {

    Optional<TaskApplication> findByTaskIdAndStudentId(Long taskId, Long studentId);

    List<TaskApplication> findByStudentIdOrderByIdDesc(Long studentId);

    List<TaskApplication> findByTaskIdOrderByIdAsc(Long taskId);

    List<TaskApplication> findByStatusOrderByIdAsc(String status);

    List<TaskApplication> findByTask_Publisher_IdOrderByIdDesc(Long publisherId);

    boolean existsByTaskId(Long taskId);

    long countByTaskIdAndStatus(Long taskId, String status);

    long countByTask_Publisher_IdAndStatus(Long publisherId, String status);

    boolean existsByPositionId(Long positionId);

    List<TaskApplication> findByPositionIdAndStatus(Long positionId, String status);

    void deleteByTaskId(Long taskId);

    @Query("""
            SELECT a.task.id, COUNT(a) FROM TaskApplication a
            WHERE a.task.id IN :taskIds AND a.status = :status
            GROUP BY a.task.id
            """)
    List<Object[]> countGroupedByTaskIdAndStatus(
            @Param("taskIds") Collection<Long> taskIds,
            @Param("status") String status
    );
}
