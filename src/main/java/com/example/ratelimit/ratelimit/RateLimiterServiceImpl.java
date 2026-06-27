package com.example.ratelimit.ratelimit;

import com.example.ratelimit.config.RateLimitConfig;
import io.bucket4j.Bandwidth;
import io.bucket4j.BucketConfiguration;
import io.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class RateLimiterServiceImpl implements RateLimiterService {

    private static final Logger LOG = Logger.getLogger(RateLimiterServiceImpl.class);

    @Inject
    RateLimitConfig rateLimitConfig;

    @Inject
    MeterRegistry meterRegistry;

    @ConfigProperty(name = "quarkus.redis.hosts", defaultValue = "redis://localhost:6379")
    String redisHosts;

    private RedisClient redisClient;
    private StatefulRedisConnection<byte[], byte[]> connection;
    private LettuceBasedProxyManager<byte[]> proxyManager;
    private final Map<String, BucketConfiguration> configCache = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        redisClient = RedisClient.create(redisHosts);
        connection = redisClient.connect(ByteArrayCodec.INSTANCE);
        proxyManager = LettuceBasedProxyManager.builderFor(connection)
                .withExpirationStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(10)))
                .build();
        LOG.infof("RateLimiterService initialized with Redis: %s", redisHosts);
    }

    @PreDestroy
    void destroy() {
        if (connection != null) connection.close();
        if (redisClient != null) redisClient.shutdown();
    }

    @Override
    public boolean tryConsume(String provider) {
        try {
            var bucket = proxyManager.builder()
                    .build(bucketKey(provider), () -> getOrCreateConfig(provider));
            boolean consumed = bucket.tryConsume(1);
            if (!consumed) {
                meterRegistry.counter("rate_limit_hits_total", "provider", provider).increment();
                LOG.debugf("Rate limit hit for provider: %s", provider);
            }
            return consumed;
        } catch (Exception e) {
            LOG.warnf(e, "Rate limiter error for provider %s, allowing request", provider);
            return true;
        }
    }

    @Override
    public long availableTokens(String provider) {
        try {
            var bucket = proxyManager.builder()
                    .build(bucketKey(provider), () -> getOrCreateConfig(provider));
            return bucket.getAvailableTokens();
        } catch (Exception e) {
            LOG.warnf(e, "Failed to get available tokens for provider %s", provider);
            return -1;
        }
    }

    @Override
    public Instant resetAt(String provider) {
        var config = rateLimitConfig.providers().get(provider);
        if (config == null) return null;
        return Instant.now().plus(config.windowDuration());
    }

    @Override
    public void forceRefill(String provider) {
        try {
            var bucket = proxyManager.builder()
                    .build(bucketKey(provider), () -> getOrCreateConfig(provider));
            bucket.reset();
            LOG.infof("Force refilled bucket for provider: %s", provider);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to force refill bucket for provider %s", provider);
        }
    }

    private BucketConfiguration getOrCreateConfig(String provider) {
        return configCache.computeIfAbsent(provider, p -> {
            var config = rateLimitConfig.providers().get(p);
            if (config == null) {
                throw new IllegalArgumentException("Unknown provider: " + p);
            }
            return BucketConfiguration.builder()
                    .addLimit(Bandwidth.builder()
                            .capacity(config.requestsPerWindow())
                            .refillGreedy(config.requestsPerWindow(), config.windowDuration())
                            .initialTokens(config.burstCapacity())
                            .build())
                    .build();
        });
    }

    private byte[] bucketKey(String provider) {
        return ("rate-limit:" + provider + ":bucket").getBytes();
    }
}
