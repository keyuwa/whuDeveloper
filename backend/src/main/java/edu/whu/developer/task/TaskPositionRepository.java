package edu.whu.developer.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskPositionRepository extends JpaRepository<TaskPosition, Long> {

    List<TaskPosition> findByTaskIdOrderBySortOrderAscIdAsc(Long taskId);

    Optional<TaskPosition> findByIdAndTaskId(Long id, Long taskId);

    void deleteByTaskId(Long taskId);
}
