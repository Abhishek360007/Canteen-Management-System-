package com.tulas.canteen.repo;

import com.tulas.canteen.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
            select a from AuditLog a
            where (:q is null or lower(a.actorName) like lower(concat('%', :q, '%'))
                   or lower(a.action) like lower(concat('%', :q, '%'))
                   or lower(a.target) like lower(concat('%', :q, '%')))
            order by a.createdAt desc, a.id desc
            """)
    Page<AuditLog> search(@Param("q") String q, Pageable pageable);
}
