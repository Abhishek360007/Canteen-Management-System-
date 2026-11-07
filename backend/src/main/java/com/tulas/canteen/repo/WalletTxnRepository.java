package com.tulas.canteen.repo;

import com.tulas.canteen.domain.WalletTxn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WalletTxnRepository extends JpaRepository<WalletTxn, Long> {

    List<WalletTxn> findTop100ByUserIdOrderByCreatedAtDesc(Long userId);
}
