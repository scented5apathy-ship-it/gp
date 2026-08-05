package com.genealogy.platform.services.tenant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Entry point for {@code tenant-service}. The
 * {@code @SpringBootApplication} annotation pulls in the
 * {@code platform-spring-boot-starter} auto-configuration (OTel,
 * OpenFeature, audit, trusted context) so this class stays focused
 * on what is unique to the service: the tenant aggregate, the
 * Keycloak mapping, and the gRPC + REST adapters.
 *
 * <p>{@code @EnableAsync} is wired so event publication (E4.7
 * outbox relay, audit forwarder) can run on the platform's default
 * executor without re-annotating each service in E3.x.
 */
@SpringBootApplication
@EnableAsync
public class TenantServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TenantServiceApplication.class, args);
    }
}
