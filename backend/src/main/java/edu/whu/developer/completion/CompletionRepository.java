package edu.whu.developer.completion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompletionRepository extends JpaRepository<Completion, Long> {

    Optional<Completion> findByProjectId(Long projectId);

    void deleteByProjectId(Long projectId);
}
