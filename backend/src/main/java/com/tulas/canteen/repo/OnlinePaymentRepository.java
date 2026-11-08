package com.tulas.canteen.repo;

import com.tulas.canteen.domain.OnlinePayment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OnlinePaymentRepository extends JpaRepository<OnlinePayment, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<OnlinePayment> findByRazorpayOrderId(String orderId);

    @EntityGraph(attributePaths = "user")
    List<OnlinePayment> findTop100ByOrderByCreatedAtDesc();

    List<OnlinePayment> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndCreatedAtAfter(Long userId, Instant after);

    /**
     * Marks a payment as paid only if it isn't already. Returns 1 for the first caller and 0 for anyone else,
     * so the browser confirmation and Razorpay's webhook can never both credit the wallet.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update OnlinePayment p set p.status = com.tulas.canteen.domain.OnlinePayment.Status.PAID,
                   p.razorpayPaymentId = :paymentId, p.confirmedBy = :by, p.paidAt = :now
            where p.id = :id and p.status <> com.tulas.canteen.domain.OnlinePayment.Status.PAID
            """)
    int markPaid(@Param("id") Long id, @Param("paymentId") String paymentId, @Param("by") String by, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("""
            update OnlinePayment p set p.status = com.tulas.canteen.domain.OnlinePayment.Status.FAILED
            where p.id = :id and p.status = com.tulas.canteen.domain.OnlinePayment.Status.CREATED
            """)
    int markFailed(@Param("id") Long id);
}
