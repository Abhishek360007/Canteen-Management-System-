package com.tulas.canteen.repo;

import com.tulas.canteen.domain.DailyCounter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyCounterRepository extends JpaRepository<DailyCounter, LocalDate> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from DailyCounter c where c.day = :day")
    Optional<DailyCounter> lockDay(@Param("day") LocalDate day);
}
