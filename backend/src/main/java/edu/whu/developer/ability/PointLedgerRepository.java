package edu.whu.developer.ability;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PointLedgerRepository extends JpaRepository<PointLedger, Long> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    Optional<PointLedger> findByIdempotencyKey(String idempotencyKey);

    List<PointLedger> findTop20ByUserIdOrderByIdDesc(Long userId);
}
