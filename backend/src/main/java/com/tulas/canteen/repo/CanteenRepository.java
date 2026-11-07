package com.tulas.canteen.repo;

import com.tulas.canteen.domain.Canteen;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CanteenRepository extends JpaRepository<Canteen, Long> {

    List<Canteen> findAllByOrderBySortOrderAscIdAsc();

    List<Canteen> findByActiveTrueOrderBySortOrderAscIdAsc();

    boolean existsByCodeIgnoreCase(String code);
}
