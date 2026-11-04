package edu.whu.developer.ability;

import edu.whu.developer.auth.User;
import edu.whu.developer.auth.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PointService {

    private final UserRepository users;
    private final PointLedgerRepository ledger;

    public PointService(UserRepository users, PointLedgerRepository ledger) {
        this.users = users;
        this.ledger = ledger;
    }

    @Transactional
    public void award(Long userId, int delta, String reason, String sourceType, String sourceId, String idempotencyKey) {
        award(userId, delta, reason, sourceType, sourceId, idempotencyKey, null);
    }

    @Transactional
    public void award(Long userId, int delta, String reason, String sourceType, String sourceId, String idempotencyKey, Long operatorId) {
        if (delta == 0) {
            return;
        }
        if (ledger.existsByIdempotencyKey(idempotencyKey)) {
            return;
        }
        User user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        int next = Math.max(0, user.getPoints() + delta);
        user.setPoints(next);
        users.save(user);

        PointLedger row = new PointLedger();
        row.setUser(user);
        row.setDelta(delta);
        row.setBalanceAfter(next);
        row.setReason(reason);
        row.setSourceType(sourceType);
        row.setSourceId(sourceId);
        row.setIdempotencyKey(idempotencyKey);
        if (operatorId != null) {
            row.setOperator(users.findById(operatorId).orElse(null));
        }
        ledger.save(row);
    }
}
