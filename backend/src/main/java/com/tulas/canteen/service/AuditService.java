package com.tulas.canteen.service;

import com.tulas.canteen.domain.AuditLog;
import com.tulas.canteen.domain.User;
import com.tulas.canteen.repo.AuditLogRepository;
import com.tulas.canteen.security.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records admin and staff actions for the Activity page. */
@Service
public class AuditService {

    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void record(AuthUser actor, String action, String target, String details) {
        repo.save(new AuditLog(actor == null ? null : actor.id(), actor == null ? "system" : actor.name(),
                action, target, details));
    }

    /** "₹500" or "₹62.50": no trailing ".00". */
    public static String rupees(java.math.BigDecimal amount) {
        java.math.BigDecimal whole = amount.setScale(0, java.math.RoundingMode.DOWN);
        return "₹" + (amount.compareTo(whole) == 0 ? whole.toPlainString()
                : amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
    }

    /** "Asha Rao (asha@college.edu)" */
    public static String who(User u) {
        return u.getName() + " (" + u.getEmail() + ")";
    }
}
