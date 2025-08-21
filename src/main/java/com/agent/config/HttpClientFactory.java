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

@Configuration
public class HttpClientFactory {

    @Bean
    public WebClient webClient() {
        // Configure a retry policy for 429 and 5xx errors
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