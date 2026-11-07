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
import java.util.Collection;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    long countByRole(Role role);

    /**
     * No nullable parameters on purpose: PostgreSQL can't type a null bound into "? is null".
     * Callers pass a ready LIKE pattern ("%" for everything, see {@code Search.pattern}) and the roles to include.
     */
    @Query("""
            select u from User u
            where u.role in :roles
              and (lower(u.name) like :pattern escape '\\'
                   or lower(u.email) like :pattern escape '\\'
                   or lower(coalesce(u.rollNo, '')) like :pattern escape '\\')
            order by u.createdAt desc
            """)
    Page<User> search(@Param("pattern") String pattern, @Param("roles") Collection<Role> roles, Pageable pageable);

    /** Atomic debit: only succeeds if the balance covers the amount. Returns rows changed (0 or 1). */
    @Modifying(flushAutomatically = true)
    @Query("update User u set u.walletBalance = u.walletBalance - :amount where u.id = :id and u.walletBalance >= :amount")
    int debit(@Param("id") Long id, @Param("amount") BigDecimal amount);

    @Modifying(flushAutomatically = true)
    @Query("update User u set u.walletBalance = u.walletBalance + :amount where u.id = :id")
    int credit(@Param("id") Long id, @Param("amount") BigDecimal amount);

    // ---- reward points (atomic, like the wallet)

    @Modifying(flushAutomatically = true)
    @Query("update User u set u.loyaltyPoints = coalesce(u.loyaltyPoints, 0) + :points where u.id = :id")
    int addPoints(@Param("id") Long id, @Param("points") int points);

    /** Returns 0 if the student doesn't have enough points. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update User u set u.loyaltyPoints = coalesce(u.loyaltyPoints, 0) - :points
            where u.id = :id and coalesce(u.loyaltyPoints, 0) >= :points
            """)
    int spendPoints(@Param("id") Long id, @Param("points") int points);

    @Query("select coalesce(u.loyaltyPoints, 0) from User u where u.id = :id")
    int pointsOf(@Param("id") Long id);

    @Modifying(flushAutomatically = true)
    @Query("update User u set u.lifetimeSpend = coalesce(u.lifetimeSpend, 0) + :amount where u.id = :id")
    int addLifetimeSpend(@Param("id") Long id, @Param("amount") BigDecimal amount);

    Optional<User> findByReferralCodeIgnoreCase(String code);

    @Query("select u.id from User u where u.role in :roles and u.blocked = false")
    java.util.List<Long> findIdsByRoleIn(@Param("roles") Collection<Role> roles);

    /** Admins plus the kitchen staff of one canteen (e.g. for its low-stock alerts). */
    @Query("""
            select u.id from User u where u.blocked = false
              and (u.role = com.tulas.canteen.domain.Role.ADMIN
                   or (u.role = com.tulas.canteen.domain.Role.STAFF and u.canteenId = :canteenId))
            """)
    java.util.List<Long> findAdminAndStaffIdsOf(@Param("canteenId") Long canteenId);

    boolean existsByReferralCodeIgnoreCase(String code);

    @Query("select u from User u where u.birthday is not null and u.role = com.tulas.canteen.domain.Role.STUDENT")
    java.util.List<User> findAllWithBirthday();

    @Query("select coalesce(sum(u.walletBalance), 0) from User u where u.role = :role")
    BigDecimal totalBalance(@Param("role") Role role);

    @Query("select u.walletBalance from User u where u.id = :id")
    BigDecimal balanceOf(@Param("id") Long id);
}
