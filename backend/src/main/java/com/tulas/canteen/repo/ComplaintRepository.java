package com.tulas.canteen.repo;

import com.tulas.canteen.domain.Complaint;
import com.tulas.canteen.domain.Complaint.Status;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    @EntityGraph(attributePaths = {"order", "order.canteen", "user"})
    Optional<Complaint> findWithOrderById(Long id);

    @EntityGraph(attributePaths = {"order", "order.canteen", "user"})
    Optional<Complaint> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);

    long countByUserIdAndStatus(Long userId, Status status);

    long countByStatus(Status status);

    long countByStatusAndCanteenId(Status status, Long canteenId);

    @EntityGraph(attributePaths = {"order", "order.canteen", "user"})
    List<Complaint> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);

    /** No nullable parameters (PostgreSQL rejects "? is null" on untyped nulls): pass every status for "all", and allCanteens. */
    @EntityGraph(attributePaths = {"order", "order.canteen", "user"})
    @Query("""
            select c from Complaint c
            where c.status in :statuses and (:allCanteens = true or c.canteenId = :canteenId)
            order by c.updatedAt desc
            """)
    List<Complaint> search(@Param("statuses") java.util.Collection<Status> statuses, @Param("allCanteens") boolean allCanteens,
                           @Param("canteenId") long canteenId, Pageable page);

    @Query("select c from Complaint c where c.createdAt >= :since and (:allCanteens = true or c.canteenId = :canteenId)")
    List<Complaint> since(@Param("since") Instant since, @Param("allCanteens") boolean allCanteens, @Param("canteenId") long canteenId);
}
