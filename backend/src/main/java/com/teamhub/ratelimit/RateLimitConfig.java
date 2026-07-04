package com.teamhub.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * Bucket4j + Lettuce rate-limit infrastructure (TASK-069).
 *
 * <p>Creates a dedicated {@link StatefulRedisConnection} backed by the same Lettuce client that
 * Spring Data Redis manages. The connection uses {@code String} keys and {@code byte[]} values as
 * required by {@link LettuceBasedProxyManager}. Because Lettuce connections are thread-safe, a
 * single shared connection is sufficient for all concurrent rate-limit operations.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

  /**
   * Dedicated Lettuce connection for Bucket4j rate-limit operations.
   *
   * <p>Uses {@code String} keys and {@code byte[]} values via a composite codec. The connection is
   * obtained from the auto-configured {@link RedisClient} inside Spring Boot's {@link
   * LettuceConnectionFactory} so it shares the same Redis server without requiring a second URI
   * configuration.
   */
  @Bean
  public StatefulRedisConnection<String, byte[]> rateLimitRedisConnection(
      RedisConnectionFactory redisConnectionFactory,
      @Value("${REDIS_URL:}") String redisUrl,
      @Value("${spring.data.redis.url:}") String springRedisUrl) {
    System.out.println("REDIS_URL env: " + redisUrl);
    System.out.println("spring.data.redis.url: " + springRedisUrl);

    LettuceConnectionFactory factory = (LettuceConnectionFactory) redisConnectionFactory;
    System.out.println(
        "RateLimit Redis target: "
            + factory.getHostName()
            + ":"
            + factory.getPort()
            + " (database "
            + factory.getDatabase()
            + ")");

    RedisClient client = (RedisClient) factory.getRequiredNativeClient();
    return client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
  }

  /**
   * Bucket4j proxy manager backed by Redis via Lettuce.
   *
   * <p>The proxy manager is stateless; it uses the shared {@link StatefulRedisConnection} to
   * read/write bucket state atomically in Redis. Bucket state is stored with a TTL derived from the
   * time needed to refill the bucket to its maximum capacity, ensuring expired keys are cleaned up
   * automatically.
   */
  @Bean
  public ProxyManager<String> rateLimitProxyManager(
      StatefulRedisConnection<String, byte[]> rateLimitRedisConnection) {
    return LettuceBasedProxyManager.builderFor(rateLimitRedisConnection).build();
  }

  /**
   * The rate-limit servlet filter bean.
   *
   * <p>Registered as a named bean so {@link com.teamhub.security.SecurityConfig} can inject it
   * directly into the Spring Security filter chain via {@code addFilterBefore}.
   */
  @Bean
  public RateLimitFilter rateLimitFilter(
      ProxyManager<String> rateLimitProxyManager,
      RateLimitProperties rateLimitProperties,
      ObjectMapper objectMapper) {
    return new RateLimitFilter(rateLimitProxyManager, rateLimitProperties, objectMapper);
  }
}
