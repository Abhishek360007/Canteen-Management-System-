package com.tulas.canteen.repo;

import com.tulas.canteen.domain.DishIngredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface DishIngredientRepository extends JpaRepository<DishIngredient, Long> {

    List<DishIngredient> findByMenuItemIdOrderByIdAsc(Long menuItemId);

    List<DishIngredient> findByMenuItemIdIn(Collection<Long> ids);

    @Modifying(flushAutomatically = true)
    @Query("delete from DishIngredient d where d.menuItemId = :id")
    int deleteByMenuItem(@Param("id") Long id);
}
