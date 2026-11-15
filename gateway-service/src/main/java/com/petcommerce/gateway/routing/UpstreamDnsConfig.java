package com.petcommerce.gateway.routing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Keeps routing correct when a backend container gets a new IP address (for example after a restart).
 *
 * <p>The gateway's HTTP client (Reactor Netty) caches DNS answers for as long as the record's TTL allows, and Docker's
 * embedded DNS answers with long TTLs. Without a cap, a restarted service that came back on another IP stayed
 * unreachable through the gateway for minutes (connection refused → 500), while its old IP could belong to another
 * container. Capping the cache to a few seconds makes the gateway follow the new address almost at once.
 */
@Configuration
public class UpstreamDnsConfig {

    @Bean
    HttpClientCustomizer shortUpstreamDnsCache(@Value("${gateway.upstream-dns-cache-ttl:PT5S}") Duration maxTtl) {
        return httpClient -> httpClient.resolver(spec -> spec
                .cacheMaxTimeToLive(maxTtl)
                .cacheNegativeTimeToLive(Duration.ofSeconds(1)));
    }
}
