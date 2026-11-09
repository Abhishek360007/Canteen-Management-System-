package com.tulas.canteen.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** One message in a complaint thread, from the student or from the canteen. */
@Entity
@Table(name = "complaint_messages", indexes = @Index(columnList = "complaint_id,created_at"))
public class ComplaintMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "complaint_id", nullable = false)
    private Long complaintId;

    @Column(nullable = false)
    private Long authorId;

    @Column(nullable = false, length = 80)
    private String authorName;

    @Column(nullable = false)
    private boolean fromStaff;

    @Column(nullable = false, length = 600)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public ComplaintMessage() {}

    public ComplaintMessage(Long complaintId, Long authorId, String authorName, boolean fromStaff, String body) {
        this.complaintId = complaintId;
        this.authorId = authorId;
        this.authorName = authorName;
        this.fromStaff = fromStaff;
        this.body = body;
    }

    public Long getId() { return id; }
    public Long getComplaintId() { return complaintId; }
    public Long getAuthorId() { return authorId; }
    public String getAuthorName() { return authorName; }
    public boolean isFromStaff() { return fromStaff; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
}
