package com.tulas.canteen.repo;

import com.tulas.canteen.domain.LoyaltyTxn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoyaltyTxnRepository extends JpaRepository<LoyaltyTxn, Long> {

    List<LoyaltyTxn> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);
}
