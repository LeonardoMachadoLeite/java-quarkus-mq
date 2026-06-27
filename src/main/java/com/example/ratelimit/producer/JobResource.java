package com.example.ratelimit.producer;

import com.example.ratelimit.config.RateLimitConfig;
import com.example.ratelimit.domain.ApiResponse;
import com.example.ratelimit.domain.JobStatus;
import com.example.ratelimit.domain.SubmitJobRequest;
import com.example.ratelimit.entity.Job;
import com.example.ratelimit.ratelimit.RateLimiterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class JobResource {

    private static final Logger LOG = Logger.getLogger(JobResource.class);

    @Inject
    JobService jobService;

    @Inject
    RateLimitConfig rateLimitConfig;

    @Inject
    RateLimiterService rateLimiterService;

    @Inject
    ObjectMapper objectMapper;

    @POST
    @Path("/jobs")
    @Transactional
    public Response submitJob(SubmitJobRequest request) {
        if (request == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Request body required")).build();
        }
        try {
            JobSubmitResponse result = jobService.submit(request);
            return Response.accepted(result).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage())).build();
        }
    }

    @GET
    @Path("/jobs/{id}")
    public Response getJob(@PathParam("id") UUID id) {
        Job job = Job.findByIdOrNull(id);
        if (job == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Job not found")).build();
        }

        try {
            ApiResponse apiResponse = null;
            if (job.response != null) {
                apiResponse = objectMapper.readValue(job.response, ApiResponse.class);
            }

            var responseBody = Map.of(
                    "jobId", job.id.toString(),
                    "status", job.status,
                    "provider", job.provider,
                    "retryCount", job.retryCount,
                    "createdAt", job.createdAt.toString(),
                    "updatedAt", job.updatedAt.toString(),
                    "response", apiResponse != null ? apiResponse : Map.of()
            );

            return Response.ok(responseBody).build();
        } catch (Exception e) {
            LOG.errorf(e, "Failed to deserialize job response for job %s", id);
            return Response.serverError().entity(Map.of("error", "Failed to read job data")).build();
        }
    }

    @DELETE
    @Path("/jobs/{id}")
    @Transactional
    public Response cancelJob(@PathParam("id") UUID id) {
        Job job = Job.findByIdOrNull(id);
        if (job == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Job not found")).build();
        }

        if (!job.status.equals("Queued") && !job.status.equals("Pending")) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", "Job cannot be cancelled in status: " + job.status)).build();
        }

        job.updateStatus(new JobStatus.Failed("Cancelled by client"));
        return Response.noContent().build();
    }

    @GET
    @Path("/providers")
    public Response listProviders() {
        var providers = rateLimitConfig.providers().entrySet().stream()
                .map(e -> Map.of(
                        "provider", e.getKey(),
                        "requestsPerWindow", e.getValue().requestsPerWindow(),
                        "windowDuration", e.getValue().windowDuration().toString(),
                        "availableTokens", rateLimiterService.availableTokens(e.getKey())
                ))
                .collect(Collectors.toList());
        return Response.ok(providers).build();
    }

    @GET
    @Path("/providers/{provider}/stats")
    public Response getProviderStats(@PathParam("provider") String provider) {
        if (!rateLimitConfig.providers().containsKey(provider)) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Provider not found")).build();
        }

        long queueDepth = Job.count("provider = ?1 and status in ('Queued', 'Processing')", provider);
        long completed = Job.count("provider = ?1 and status = 'Completed'", provider);
        long failed = Job.count("provider = ?1 and status in ('Failed', 'DeadLettered')", provider);
        long availableTokens = rateLimiterService.availableTokens(provider);
        Instant resetAt = rateLimiterService.resetAt(provider);

        return Response.ok(Map.of(
                "provider", provider,
                "queueDepth", queueDepth,
                "completedTotal", completed,
                "failedTotal", failed,
                "availableTokens", availableTokens,
                "resetAt", resetAt != null ? resetAt.toString() : ""
        )).build();
    }
}
