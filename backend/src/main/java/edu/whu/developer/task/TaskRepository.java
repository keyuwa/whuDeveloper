package edu.whu.developer.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByStatusOrderByIdDesc(String status);

    List<Task> findByPublisherIdOrderByIdDesc(Long publisherId);

    @Query("SELECT COALESCE(MAX(t.roleSerial), 0) FROM Task t WHERE t.roleType = :roleType")
    int maxRoleSerial(@Param("roleType") String roleType);

    @Query("""
            SELECT t from Task t
            WHERE t.status = 'OPEN'
              AND (:roleType IS NULL OR :roleType = '' OR t.roleType = :roleType)
              AND (:difficulty IS NULL OR :difficulty = '' OR t.difficulty = :difficulty)
              AND (:techField IS NULL OR :techField = '' OR t.techField = :techField)
              AND (:supportLanguage IS NULL OR :supportLanguage = '' OR t.supportLanguages LIKE CONCAT('%', :supportLanguage, '%'))
              AND (:programmingLanguage IS NULL OR :programmingLanguage = '' OR t.programmingLanguages LIKE CONCAT('%', :programmingLanguage, '%'))
              AND (:scheduleType IS NULL OR :scheduleType = '' OR t.scheduleType = :scheduleType)
              AND (:durationMin IS NULL OR t.durationWeeks >= :durationMin)
              AND (:durationMax IS NULL OR t.durationWeeks <= :durationMax)
              AND (:hoursMin IS NULL OR t.estimatedHours >= :hoursMin)
              AND (:hoursMax IS NULL OR t.estimatedHours <= :hoursMax)
            ORDER BY t.id DESC
            """)
    List<Task> searchOpen(
            @Param("roleType") String roleType,
            @Param("difficulty") String difficulty,
            @Param("techField") String techField,
            @Param("supportLanguage") String supportLanguage,
            @Param("programmingLanguage") String programmingLanguage,
            @Param("scheduleType") String scheduleType,
            @Param("durationMin") Integer durationMin,
            @Param("durationMax") Integer durationMax,
            @Param("hoursMin") Integer hoursMin,
            @Param("hoursMax") Integer hoursMax
    );
}
