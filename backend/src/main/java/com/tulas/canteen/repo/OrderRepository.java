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

    @EntityGraph(attributePaths = {"items", "user", "canteen"})
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findFull(@Param("id") Long id);

    @EntityGraph(attributePaths = {"items", "user", "canteen"})
    List<Order> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"items", "user", "canteen"})
    List<Order> findByOrderDateAndStatusInOrderByCreatedAtAsc(LocalDate date, Collection<OrderStatus> statuses);

    @EntityGraph(attributePaths = {"items", "user", "canteen"})
    List<Order> findByOrderDateOrderByCreatedAtDesc(LocalDate date);

    @EntityGraph(attributePaths = {"items", "user", "canteen"})
    @Query("""
            select o from Order o
            where o.orderDate between :from and :to
              and o.status in :statuses
            order by o.createdAt desc
            """)
    List<Order> findForReport(@Param("from") LocalDate from, @Param("to") LocalDate to,
                              @Param("statuses") Collection<OrderStatus> statuses);

    /** {@code status} null means all statuses. (No nullable query parameters: PostgreSQL can't type them.) */
    default List<Order> findForReport(LocalDate from, LocalDate to, OrderStatus status) {
        return findForReport(from, to, status == null ? List.of(OrderStatus.values()) : List.of(status));
    }

    @EntityGraph(attributePaths = {"items", "user", "canteen"})
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

    @EntityGraph(attributePaths = {"items", "user", "canteen"})
    List<Order> findByStatusAndReadyAtBefore(OrderStatus status, Instant cutoff);

    @EntityGraph(attributePaths = {"items", "user", "canteen"})
    List<Order> findByStatusInAndOrderDateBefore(Collection<OrderStatus> statuses, LocalDate date);

    @EntityGraph(attributePaths = {"items", "canteen"})
    List<Order> findByOrderDateBetween(LocalDate from, LocalDate to);

    // ---- per canteen

    @Query("select o.code from Order o where o.orderDate = :day and o.code like :pattern")
    List<String> findCodesOn(@Param("day") LocalDate day, @Param("pattern") String pattern);

    @EntityGraph(attributePaths = {"items", "user", "canteen"})
    List<Order> findByCanteenIdAndOrderDateAndStatusInOrderByCreatedAtAsc(Long canteenId, LocalDate date, Collection<OrderStatus> statuses);

    @EntityGraph(attributePaths = {"items", "user", "canteen"})
    List<Order> findByCanteenIdAndOrderDateOrderByCreatedAtDesc(Long canteenId, LocalDate date);

    @EntityGraph(attributePaths = {"items", "canteen"})
    List<Order> findByCanteenIdAndOrderDateBetween(Long canteenId, LocalDate from, LocalDate to);

    /** Report rows, optionally for one canteen (canteenIds = all canteens when not filtering). */
    @EntityGraph(attributePaths = {"items", "user", "canteen"})
    @Query("""
            select o from Order o
            where o.orderDate between :from and :to
              and o.status in :statuses
              and o.canteen.id in :canteenIds
            order by o.createdAt desc
            """)
    List<Order> findForReport(@Param("from") LocalDate from, @Param("to") LocalDate to,
                              @Param("statuses") Collection<OrderStatus> statuses,
                              @Param("canteenIds") Collection<Long> canteenIds);

    long countByUserIdAndCanteenIdAndStatusIn(Long userId, Long canteenId, Collection<OrderStatus> statuses);

    /** Place in the queue at this canteen. */
    @Query("""
            select count(o) from Order o
            where o.canteen.id = :canteenId and o.orderDate = :date and o.status in :statuses and o.id < :id
            """)
    long countAheadAt(@Param("canteenId") Long canteenId, @Param("date") LocalDate date,
                      @Param("statuses") Collection<OrderStatus> statuses, @Param("id") Long id);
}
