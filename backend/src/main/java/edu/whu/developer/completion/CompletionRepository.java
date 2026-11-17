package edu.whu.developer.completion;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompletionRepository extends JpaRepository<Completion, Long> {

    @EntityGraph(attributePaths = {"project", "project.task", "project.student"})
    List<Completion> findByProjectPublisherIdOrderByCreatedAtDesc(Long publisherId);

    long countByProjectPublisherIdAndStatusAndProjectStatusNot(Long publisherId, String status, String projectStatus);


    Optional<Completion> findByProjectId(Long projectId);

    void deleteByProjectId(Long projectId);
}
