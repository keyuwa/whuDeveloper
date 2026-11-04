package edu.whu.developer.application;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskApplicationRepository extends JpaRepository<TaskApplication, Long> {

    Optional<TaskApplication> findByTaskIdAndStudentId(Long taskId, Long studentId);

    List<TaskApplication> findByStudentIdOrderByIdDesc(Long studentId);

    List<TaskApplication> findByTaskIdOrderByIdAsc(Long taskId);

    List<TaskApplication> findByStatusOrderByIdAsc(String status);

    List<TaskApplication> findByTask_Publisher_IdOrderByIdDesc(Long publisherId);

    boolean existsByTaskId(Long taskId);

    void deleteByTaskId(Long taskId);
}
