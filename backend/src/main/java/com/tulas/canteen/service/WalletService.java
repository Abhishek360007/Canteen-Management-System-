package com.tulas.canteen.service;

import com.tulas.canteen.config.ApiException;
import com.tulas.canteen.domain.Role;
import com.tulas.canteen.domain.TxnType;
import com.tulas.canteen.domain.User;
import com.tulas.canteen.domain.WalletTxn;
import com.tulas.canteen.dto.AdminDtos.TxnView;
import com.tulas.canteen.dto.AdminDtos.WalletView;
import com.tulas.canteen.repo.UserRepository;
import com.tulas.canteen.repo.WalletTxnRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

/**
 * All wallet money movement goes through here. Balance changes are single atomic UPDATEs
 * (no read-modify-write), and each one writes a ledger row.
 */
@Service
public class WalletService {

    private final UserRepository users;
    private final WalletTxnRepository txns;
    private final LiveHub live;

    public WalletService(UserRepository users, WalletTxnRepository txns, LiveHub live) {
        this.users = users;
        this.txns = txns;
        this.live = live;
    }

    @Transactional
    public TxnView recharge(Long studentId, BigDecimal amount, String note, String adminName) {
        User u = users.findById(studentId).orElseThrow(() -> ApiException.notFound("Student not found"));
        if (u.getRole() != Role.STUDENT) throw ApiException.badRequest("Only students have a wallet.");
        users.credit(studentId, amount);
        WalletTxn t = record(studentId, TxnType.RECHARGE, amount, blankToDefault(note, "Wallet recharge"), adminName);
        return TxnView.of(t);
    }

    /** Called inside order placement; throws if the balance is too low (and the whole order rolls back). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void pay(Long userId, BigDecimal amount, String orderCode) {
        if (users.debit(userId, amount) == 0) {
            BigDecimal balance = users.balanceOf(userId);
            throw ApiException.badRequest("Not enough wallet balance. You have ₹" + balance.toPlainString()
                    + ", this order needs ₹" + amount.toPlainString() + ".");
        }
        record(userId, TxnType.PAYMENT, amount.negate(), "Order " + orderCode, "system");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void refund(Long userId, BigDecimal amount, String orderCode) {
        users.credit(userId, amount);
        record(userId, TxnType.REFUND, amount, "Refund for order " + orderCode, "system");
    }

    /** Goodwill refund when a complaint is resolved (also for orders paid in cash: it goes to the wallet). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void complaintRefund(Long userId, BigDecimal amount, String orderCode, String adminName) {
        users.credit(userId, amount);
        record(userId, TxnType.REFUND, amount, "Refund for your complaint on order " + orderCode, adminName);
    }

    @Transactional(readOnly = true)
    public WalletView view(Long userId) {
        return new WalletView(users.balanceOf(userId),
                txns.findTop100ByUserIdOrderByCreatedAtDesc(userId).stream().map(TxnView::of).toList());
    }

    private WalletTxn record(Long userId, TxnType type, BigDecimal amount, String note, String by) {
        BigDecimal after = users.balanceOf(userId);
        WalletTxn t = txns.save(new WalletTxn(users.getReferenceById(userId), type, amount, after, note, by));
        live.toUser(userId, "wallet", Map.of("balance", after));
        return t;
    }

    private static String blankToDefault(String s, String def) {
        return s == null || s.isBlank() ? def : s.trim();
    }
}
