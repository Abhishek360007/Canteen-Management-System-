package com.tulas.canteen.repo;

import com.tulas.canteen.domain.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    List<MenuItem> findByArchivedFalseOrderByCategoryAscNameAsc();

    List<MenuItem> findByCanteenIdAndArchivedFalseOrderByCategoryAscNameAsc(Long canteenId);

    @Query("select m.stockToday from MenuItem m where m.id = :id")
    Integer stockOf(@Param("id") Long id);

    /** Atomic stock reservation: returns 0 if fewer plates are left than requested. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update MenuItem m set m.stockToday = m.stockToday - :qty
            where m.id = :id and m.stockToday is not null and m.stockToday >= :qty
            """)
    int reserve(@Param("id") Long id, @Param("qty") int qty);

    @Modifying(flushAutomatically = true)
    @Query("update MenuItem m set m.stockToday = m.stockToday + :qty where m.id = :id and m.stockToday is not null")
    int release(@Param("id") Long id, @Param("qty") int qty);

    /** Atomic rating change, so two students rating at once can't overwrite each other. */
    @Modifying(flushAutomatically = true)
    @Query("update MenuItem m set m.ratingSum = m.ratingSum + :stars, m.ratingCount = m.ratingCount + :count where m.id = :id")
    int addRating(@Param("id") Long id, @Param("stars") int stars, @Param("count") int count);

    @Modifying(flushAutomatically = true)
    @Query("update MenuItem m set m.stockToday = m.defaultStock where m.archived = false")
    int resetDailyStock();
}
