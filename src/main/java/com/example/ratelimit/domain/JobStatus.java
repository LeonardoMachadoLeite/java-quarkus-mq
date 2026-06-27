package com.example.ratelimit.domain;

public sealed interface JobStatus permits
        JobStatus.Pending,
        JobStatus.Queued,
        JobStatus.Processing,
        JobStatus.Completed,
        JobStatus.Failed,
        JobStatus.DeadLettered {

    record Pending() implements JobStatus {}
    record Queued() implements JobStatus {}
    record Processing() implements JobStatus {}
    record Completed() implements JobStatus {}
    record Failed(String reason) implements JobStatus {}
    record DeadLettered(String reason) implements JobStatus {}

    static JobStatus fromString(String s) {
        return switch (s) {
            case "Pending" -> new Pending();
            case "Queued" -> new Queued();
            case "Processing" -> new Processing();
            case "Completed" -> new Completed();
            case "Failed" -> new Failed("unknown");
            case "DeadLettered" -> new DeadLettered("unknown");
            default -> throw new IllegalArgumentException("Unknown status: " + s);
        };
    }

    default String name() {
        return this.getClass().getSimpleName();
    }
}
