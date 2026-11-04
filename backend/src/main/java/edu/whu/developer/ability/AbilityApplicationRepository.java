package edu.whu.developer.ability;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AbilityApplicationRepository extends JpaRepository<AbilityApplication, Long> {

    List<AbilityApplication> findByApplicantIdOrderByIdDesc(Long applicantId);

    List<AbilityApplication> findByStatusInOrderByIdAsc(List<String> statuses);

    boolean existsByApplicantIdAndTargetLevelAndWantApprenticeAndWantMgmtAndStatusAndDecidedAtAfter(
            Long applicantId,
            String targetLevel,
            boolean wantApprentice,
            boolean wantMgmt,
            String status,
            LocalDateTime decidedAtAfter
    );

    boolean existsByApplicantIdAndStatusIn(Long applicantId, List<String> statuses);
}
