package edu.whu.developer.task;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByStatusOrderByIdDesc(String status);

    List<Task> findByPublisherIdOrderByIdDesc(Long publisherId);

    long countByPublisherId(Long publisherId);

    long countByPublisherIdAndStatus(Long publisherId, String status);

    long countByPublisherIdAndRoleType(Long publisherId, String roleType);

    long countByPublisherIdAndRoleTypeAndStatus(Long publisherId, String roleType, String status);

    @EntityGraph(attributePaths = "publisher")
    @Query(
            value = """
                    SELECT t FROM Task t
                    WHERE t.publisher.id = :publisherId
                      AND (:roleType IS NULL OR :roleType = '' OR t.roleType = :roleType)
                      AND (:status IS NULL OR :status = '' OR t.status = :status)
                      AND (:keyword IS NULL OR :keyword = ''
                           OR LOWER(t.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                           OR LOWER(t.publicCode) LIKE LOWER(CONCAT('%', :keyword, '%')))
                    ORDER BY t.id DESC
                    """,
            countQuery = """
                    SELECT count(t) FROM Task t
                    WHERE t.publisher.id = :publisherId
                      AND (:roleType IS NULL OR :roleType = '' OR t.roleType = :roleType)
                      AND (:status IS NULL OR :status = '' OR t.status = :status)
                      AND (:keyword IS NULL OR :keyword = ''
                           OR LOWER(t.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                           OR LOWER(t.publicCode) LIKE LOWER(CONCAT('%', :keyword, '%')))
                    """
    )
    Page<Task> searchMine(
            @Param("publisherId") Long publisherId,
            @Param("roleType") String roleType,
            @Param("status") String status,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("SELECT COALESCE(MAX(t.roleSerial), 0) FROM Task t WHERE t.roleType = :roleType")
    int maxRoleSerial(@Param("roleType") String roleType);

    @Query(
            value = """
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
                    """,
            countQuery = """
                    SELECT count(t) from Task t
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
                    """
    )
    Page<Task> searchOpen(
            @Param("roleType") String roleType,
            @Param("difficulty") String difficulty,
            @Param("techField") String techField,
            @Param("supportLanguage") String supportLanguage,
            @Param("programmingLanguage") String programmingLanguage,
            @Param("scheduleType") String scheduleType,
            @Param("durationMin") Integer durationMin,
            @Param("durationMax") Integer durationMax,
            @Param("hoursMin") Integer hoursMin,
            @Param("hoursMax") Integer hoursMax,
            Pageable pageable
    );
}
