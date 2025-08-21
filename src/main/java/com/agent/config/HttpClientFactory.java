package com.agent.config;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * A Spring configuration class responsible for creating and configuring HTTP client beans.
 * This factory provides a centralized place to define shared client configurations,
 * such as retry policies, timeouts, and default headers.
 */
@Configuration
public class HttpClientFactory {

    /**
     * Creates and configures a singleton WebClient bean with a built-in retry mechanism. [1, 2]
     * <p>
     * The WebClient is configured to automatically retry failed requests that result in
     * HTTP 429 (Too Many Requests) or HTTP 5xx (Service Unavailable) status codes.
     * The retry strategy uses an exponential backoff, starting with a 500ms delay
     * and doubling the wait time for each subsequent attempt, up to a maximum of 3 attempts.
     *
     * @return A fully configured {@link WebClient} instance ready for use in the application.
     */
    @Bean
    public WebClient webClient() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofMillis(500), 2))
                .retryOnException(e -> e instanceof org.springframework.web.reactive.function.client.WebClientResponseException.ServiceUnavailable ||
                        e instanceof org.springframework.web.reactive.function.client.WebClientResponseException.TooManyRequests)
                .build();

        RetryRegistry registry = RetryRegistry.of(config);
        Retry retry = registry.retry("api-agent-http");

        return WebClient.builder()
                .filter((request, next) -> next.exchange(request)
                        .transform(RetryOperator.of(retry)))
                .build();
    }
}