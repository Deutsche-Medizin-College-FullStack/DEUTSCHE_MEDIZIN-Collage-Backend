package Henok.example.DeutscheCollageBack_endAPI.Security;


import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final Bucket loginTemplate;
    private final Bucket studentTemplate;
    private final Bucket staffTemplate;
    private final Bucket publicTemplate;

    // One bucket per user/IP
    private final Map<String, Bucket> userBuckets = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public RateLimitingFilter(
            @Qualifier("loginBucket") Bucket loginTemplate,
            @Qualifier("studentBucket") Bucket studentTemplate,
            @Qualifier("staffBucket") Bucket staffTemplate,
            @Qualifier("publicBucket") Bucket publicTemplate) {

        this.loginTemplate = loginTemplate;
        this.studentTemplate = studentTemplate;
        this.staffTemplate = staffTemplate;
        this.publicTemplate = publicTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String key = resolveKey(request);

        Bucket bucket = getOrCreateBucket(path, key);

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Remaining",
                    String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType("application/json");

            long retryAfter = Duration.ofNanos(probe.getNanosToWaitForReset()).getSeconds();

            Map<String, Object> error = Map.of(
                    "timestamp", java.time.Instant.now().toString(),
                    "status", 429,
                    "error", "Too Many Requests",
                    "message", "Rate limit exceeded. Please try again later.",
                    "retryAfterSeconds", retryAfter,
                    "path", path
            );

            response.getWriter().write(objectMapper.writeValueAsString(error));
            response.getWriter().flush();
        }
    }

    private String resolveKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken)) {

            Object principal = auth.getPrincipal();

            if (principal instanceof UserDetails) {
                return ((UserDetails) principal).getUsername(); // per user
            }
        }

        // fallback → IP-based
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        return "ip:" + ip;
    }

    private Bucket getOrCreateBucket(String path, String key) {

        // 🔐 LOGIN / REGISTER → per user/IP bucket (NOT shared)
        if (path.startsWith("/api/auth/login") || path.startsWith("/api/auth/register")) {
            return userBuckets.computeIfAbsent(key + ":login",
                    k -> createNewBucketFromTemplate(loginTemplate));
        }

        // 🌍 PUBLIC ENDPOINTS → per IP bucket
        if (isPublicReadOnlyEndpoint(path)) {
            return userBuckets.computeIfAbsent(key + ":public",
                    k -> createNewBucketFromTemplate(publicTemplate));
        }

        // 👤 AUTHENTICATED USERS
        return userBuckets.computeIfAbsent(key, k -> {

            if (key.startsWith("ip:")) {
                // unauthenticated but not public → treat as public
                return createNewBucketFromTemplate(publicTemplate);
            }

            // 🎓 Student endpoints
            if (path.startsWith("/api/student/")) {
                return createNewBucketFromTemplate(studentTemplate);
            }

            // 👨‍💼 Staff/Admin endpoints
            return createNewBucketFromTemplate(staffTemplate);
        });
    }

    // Create a fresh bucket from template (kept your logic)
    private Bucket createNewBucketFromTemplate(Bucket template) {
        long capacity = template.getAvailableTokens();

        return Bucket.builder()
                .addLimit(Bandwidth.classic(
                        capacity,
                        Refill.intervally(capacity, Duration.ofMinutes(1))
                ))
                .build();
    }

    private boolean isPublicReadOnlyEndpoint(String path) {
        return path.startsWith("/api/enums/") ||
               path.startsWith("/api/country/") ||
               path.startsWith("/api/region/") ||
               path.startsWith("/api/woreda/") ||
               path.startsWith("/api/zone/") ||
               path.startsWith("/api/academic-years/") ||
               path.startsWith("/api/departments/") ||
               path.startsWith("/api/enrollment-type/") ||
               path.startsWith("/api/school-backgrounds/") ||
               path.startsWith("/api/class-years/") ||
               path.startsWith("/api/semesters/") ||
               path.startsWith("/api/filters/options");
    }
}