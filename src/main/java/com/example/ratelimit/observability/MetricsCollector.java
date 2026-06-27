package com.example.ratelimit.observability;

import com.example.ratelimit.config.RateLimitConfig;
import com.example.ratelimit.entity.Job;
import com.example.ratelimit.ratelimit.RateLimiterService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class MetricsCollector {

    private static final Logger LOG = Logger.getLogger(MetricsCollector.class);

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    RateLimitConfig rateLimitConfig;

    @Inject
    RateLimiterService rateLimiterService;

    private final Map<String, AtomicLong> queueDepthGauges = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> tokenGauges = new ConcurrentHashMap<>();

    void init() {
        for (String provider : rateLimitConfig.providers().keySet()) {
            AtomicLong queueDepth = new AtomicLong(0);
            queueDepthGauges.put(provider, queueDepth);
            Gauge.builder("queue_depth", queueDepth, AtomicLong::get)
                    .tag("provider", provider)
                    .description("Current queue depth for provider")
                    .register(meterRegistry);

            AtomicLong tokens = new AtomicLong(0);
            tokenGauges.put(provider, tokens);
            Gauge.builder("rate_limit_tokens_available", tokens, AtomicLong::get)
                    .tag("provider", provider)
                    .description("Available rate limit tokens for provider")
                    .register(meterRegistry);
        }
    }

    @Scheduled(every = "30s")
    void collectMetrics() {
        for (String provider : rateLimitConfig.providers().keySet()) {
            try {
                long depth = Job.count("provider = ?1 and status in ('Queued', 'Processing')", provider);
                queueDepthGauges.computeIfAbsent(provider, p -> {
                    AtomicLong gauge = new AtomicLong(0);
                    Gauge.builder("queue_depth", gauge, AtomicLong::get)
                            .tag("provider", p)
                            .register(meterRegistry);
                    return gauge;
                }).set(depth);

                long tokens = rateLimiterService.availableTokens(provider);
                tokenGauges.computeIfAbsent(provider, p -> {
                    AtomicLong gauge = new AtomicLong(0);
                    Gauge.builder("rate_limit_tokens_available", gauge, AtomicLong::get)
                            .tag("provider", p)
                            .register(meterRegistry);
                    return gauge;
                }).set(Math.max(0, tokens));
            } catch (Exception e) {
                LOG.warnf(e, "Failed to collect metrics for provider %s", provider);
            }
        }
    }
}
