package Henok.example.DeutscheCollageBack_endAPI.Security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;

import java.time.Duration;

@Configuration
public class RateLimitConfig {

    // Login / Register - Very strict (protect brute force)
    @Bean(name = "loginBucket")
    public Bucket loginBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(8, Refill.intervally(8, Duration.ofMinutes(1))))
                .build();
    }

    // Students - Moderate
    @Bean(name = "studentBucket")
    public Bucket studentBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(80, Refill.intervally(80, Duration.ofMinutes(1))))
                .build();
    }

    // All Admin / Staff roles - Higher limits
    @Bean(name = "staffBucket")
    public Bucket staffBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(200, Refill.intervally(200 , Duration.ofMinutes(1))))
                .build();
    }

    // Public read-only endpoints (enums, regions, etc.)
    @Bean(name = "publicBucket")
    public Bucket publicBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(400, Refill.intervally(400, Duration.ofMinutes(1))))
                .build();
    }
}
