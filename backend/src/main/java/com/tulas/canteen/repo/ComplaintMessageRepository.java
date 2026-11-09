package com.tulas.canteen.repo;

import com.tulas.canteen.domain.ComplaintMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplaintMessageRepository extends JpaRepository<ComplaintMessage, Long> {

    List<ComplaintMessage> findByComplaintIdOrderByCreatedAtAscIdAsc(Long complaintId);

    long countByComplaintId(Long complaintId);
}
