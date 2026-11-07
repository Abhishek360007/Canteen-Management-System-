package com.tulas.canteen.repo;

import com.tulas.canteen.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** {@code pattern} is a ready lower-case LIKE pattern; "%" lists everything (see {@code Search.pattern}). */
    @Query("""
            select a from AuditLog a
            where lower(a.actorName) like :pattern escape '\\'
               or lower(a.action) like :pattern escape '\\'
               or lower(coalesce(a.target, '')) like :pattern escape '\\'
               or lower(coalesce(a.details, '')) like :pattern escape '\\'
            order by a.createdAt desc, a.id desc
            """)
    Page<AuditLog> search(@Param("pattern") String pattern, Pageable pageable);
}
