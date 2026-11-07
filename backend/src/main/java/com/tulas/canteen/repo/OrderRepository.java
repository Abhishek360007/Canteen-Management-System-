package com.tulas.canteen.repo;

import com.tulas.canteen.domain.Order;
import com.tulas.canteen.domain.OrderStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"items", "user"})
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findFull(@Param("id") Long id);

    @EntityGraph(attributePaths = {"items", "user"})
    List<Order> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"items", "user"})
    List<Order> findByOrderDateAndStatusInOrderByCreatedAtAsc(LocalDate date, Collection<OrderStatus> statuses);

    @EntityGraph(attributePaths = {"items", "user"})
    List<Order> findByOrderDateOrderByCreatedAtDesc(LocalDate date);

    @EntityGraph(attributePaths = {"items", "user"})
    @Query("""
            select o from Order o
            where o.orderDate between :from and :to
              and (:status is null or o.status = :status)
            order by o.createdAt desc
            """)
    List<Order> findForReport(@Param("from") LocalDate from, @Param("to") LocalDate to,
                              @Param("status") OrderStatus status);

    @EntityGraph(attributePaths = {"items", "user"})
    Optional<Order> findByOrderDateAndCodeIgnoreCase(LocalDate date, String code);

    Optional<Order> findByUserIdAndIdempotencyKey(Long userId, String key);

    long countByUserIdAndStatusIn(Long userId, Collection<OrderStatus> statuses);

    /** Active orders today placed before this one: the student's place in the queue. */
    @Query("""
            select count(o) from Order o
            where o.orderDate = :date and o.status in :statuses and o.id < :id
            """)
    long countAhead(@Param("date") LocalDate date, @Param("statuses") Collection<OrderStatus> statuses,
                    @Param("id") Long id);

    @EntityGraph(attributePaths = {"items", "user"})
    List<Order> findByStatusAndReadyAtBefore(OrderStatus status, Instant cutoff);

    @EntityGraph(attributePaths = {"items", "user"})
    List<Order> findByStatusInAndOrderDateBefore(Collection<OrderStatus> statuses, LocalDate date);

    @EntityGraph(attributePaths = {"items"})
    List<Order> findByOrderDateBetween(LocalDate from, LocalDate to);
}
