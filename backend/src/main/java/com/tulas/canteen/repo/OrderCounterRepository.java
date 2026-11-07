package com.tulas.canteen.repo;

import com.tulas.canteen.domain.OrderCounter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderCounterRepository extends JpaRepository<OrderCounter, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from OrderCounter c where c.key = :key")
    Optional<OrderCounter> lock(@Param("key") String key);
}
