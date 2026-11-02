package edu.whu.developer.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByApplicationId(Long applicationId);

    List<Project> findByStudentIdOrPublisherIdOrderByIdDesc(Long studentId, Long publisherId);
}
