package com.example.ratelimit.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dead_letters")
public class DeadLetter extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "job_id")
    public UUID jobId;

    public String provider;

    @Column(name = "retry_count")
    public int retryCount;

    @Column(columnDefinition = "jsonb", name = "request_payload")
    public String requestPayload;

    @Column(name = "error_message")
    public String errorMessage;

    @Column(name = "dead_lettered_at")
    public Instant deadLetteredAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (deadLetteredAt == null) deadLetteredAt = Instant.now();
    }
}
