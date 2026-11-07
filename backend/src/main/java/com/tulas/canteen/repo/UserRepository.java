package com.tulas.canteen.repo;

import com.tulas.canteen.domain.Role;
import com.tulas.canteen.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    long countByRole(Role role);

    @Query("""
            select u from User u
            where (:role is null or u.role = :role)
              and (:q is null or lower(u.name) like lower(concat('%', :q, '%'))
                   or lower(u.email) like lower(concat('%', :q, '%'))
                   or lower(u.rollNo) like lower(concat('%', :q, '%')))
            order by u.createdAt desc
            """)
    Page<User> search(@Param("q") String q, @Param("role") Role role, Pageable pageable);

    /** Atomic debit: only succeeds if the balance covers the amount. Returns rows changed (0 or 1). */
    @Modifying(flushAutomatically = true)
    @Query("update User u set u.walletBalance = u.walletBalance - :amount where u.id = :id and u.walletBalance >= :amount")
    int debit(@Param("id") Long id, @Param("amount") BigDecimal amount);

    @Modifying(flushAutomatically = true)
    @Query("update User u set u.walletBalance = u.walletBalance + :amount where u.id = :id")
    int credit(@Param("id") Long id, @Param("amount") BigDecimal amount);

    @Query("select coalesce(sum(u.walletBalance), 0) from User u where u.role = :role")
    BigDecimal totalBalance(@Param("role") Role role);

    @Query("select u.walletBalance from User u where u.id = :id")
    BigDecimal balanceOf(@Param("id") Long id);
}
