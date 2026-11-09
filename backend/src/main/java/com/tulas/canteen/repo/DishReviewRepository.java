package com.tulas.canteen.repo;

import com.tulas.canteen.domain.DishReview;
import com.tulas.canteen.domain.DishReview.Status;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DishReviewRepository extends JpaRepository<DishReview, Long> {

    Optional<DishReview> findByOrderIdAndMenuItemId(Long orderId, Long menuItemId);

    List<DishReview> findByOrderId(Long orderId);

    List<DishReview> findByMenuItemIdAndStatusAndCommentIsNotNullOrderByCreatedAtDesc(Long menuItemId, Status status, Pageable page);

    /** [stars, count] rows for one dish: every review counts, whatever happened to its words. */
    @Query("select r.stars, count(r) from DishReview r where r.menuItemId = :id group by r.stars")
    List<Object[]> starCounts(@Param("id") Long menuItemId);

    List<DishReview> findTop100ByStatusOrderByCreatedAtAsc(Status status);

    List<DishReview> findTop100ByCommentIsNotNullOrderByCreatedAtDesc();

    long countByStatus(Status status);
}
