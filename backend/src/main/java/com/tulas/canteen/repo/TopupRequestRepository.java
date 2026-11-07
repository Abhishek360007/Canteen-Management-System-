package com.tulas.canteen.repo;

import com.tulas.canteen.domain.TopupRequest;
import com.tulas.canteen.domain.TopupRequest.Status;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TopupRequestRepository extends JpaRepository<TopupRequest, Long> {

    @EntityGraph(attributePaths = "user")
    List<TopupRequest> findTop100ByStatusOrderByCreatedAtAsc(Status status);

    @EntityGraph(attributePaths = "user")
    List<TopupRequest> findTop100ByOrderByCreatedAtDesc();

    List<TopupRequest> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = "user")
    Optional<TopupRequest> findWithUserById(Long id);

    boolean existsByUpiRefIgnoreCase(String upiRef);

    long countByUserIdAndStatus(Long userId, Status status);

    long countByStatus(Status status);
}
