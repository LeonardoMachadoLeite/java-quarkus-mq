package com.example.ratelimit.entity;

import com.example.ratelimit.domain.ApiRequest;
import com.example.ratelimit.domain.ApiResponse;
import com.example.ratelimit.domain.JobStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "jobs", indexes = {
        @Index(name = "idx_jobs_provider_status_created", columnList = "provider, status, created_at"),
        @Index(name = "idx_jobs_status_created", columnList = "status, created_at")
})
public class Job extends PanacheEntityBase {

    @Id
    public UUID id;

    public String provider;

    public String status;

    @Column(name = "retry_count")
    public int retryCount;

    @Column(columnDefinition = "jsonb", name = "request_payload")
    public String request;

    @Column(columnDefinition = "jsonb")
    public String response;

    @Column(name = "callback_url")
    public String callbackUrl;

    @Column(name = "created_at")
    public Instant createdAt;

    @Column(name = "updated_at")
    public Instant updatedAt;

    @Column(name = "error_message")
    public String errorMessage;

    public static Job findByIdOrNull(UUID id) {
        return findById(id);
    }

    public static List<Job> findByProvider(String provider) {
        return list("provider", provider);
    }

    public static List<Job> findByStatus(String status) {
        return list("status", status);
    }

    public void updateStatus(JobStatus status) {
        this.status = status.name();
        this.updatedAt = Instant.now();
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
