package edu.whu.developer.message;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskMessageRepository extends JpaRepository<TaskMessage, Long> {

    List<TaskMessage> findByTaskIdAndStudentIdOrderByIdAsc(Long taskId, Long studentId);

    void deleteByTaskId(Long taskId);

    void deleteByTaskIdAndStudentId(Long taskId, Long studentId);

    @Query("""
            select m from TaskMessage m
            where m.student.id = :userId or m.sender.id = :userId or m.task.publisher.id = :userId
            order by m.id desc
            """)
    List<TaskMessage> inboxFor(@Param("userId") Long userId);
}
