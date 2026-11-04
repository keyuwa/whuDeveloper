package edu.whu.developer.shortlist;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskShortlistRepository extends JpaRepository<TaskShortlist, Long> {

    Optional<TaskShortlist> findByStudentIdAndTaskId(Long studentId, Long taskId);

    boolean existsByStudentIdAndTaskId(Long studentId, Long taskId);

    List<TaskShortlist> findByStudentIdOrderByIdDesc(Long studentId);

    void deleteByStudentIdAndTaskId(Long studentId, Long taskId);

    void deleteByTaskId(Long taskId);
}
