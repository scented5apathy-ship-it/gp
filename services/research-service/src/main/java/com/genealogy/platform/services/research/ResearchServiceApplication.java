package com.genealogy.platform.services.research;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for {@code research-service}. The
 * {@code @SpringBootApplication} annotation pulls in the
 * {@code platform-spring-boot-starter} auto-configuration (OTel,
 * OpenFeature, audit, trusted context) so this class stays focused
 * on what is unique to the service: the research aggregate, the
 * Flyway-managed RLS-protected {@code research_service} schema, and
 * the JdbcTemplate-backed REST surface shipped in E6.1c.
 *
 * <p>{@code @EnableAsync} is wired so the outbox relay (E6.1d) can
 * run on the platform's default executor without re-annotating
 * each service later. E6.1e adds {@code @EnableScheduling} so the
 * {@code ResearchOutboxRelayRunner} can drive the relay via
 * {@code @Scheduled} per ADR-E0.5-08 + {@code design.md} §7.3.
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class ResearchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResearchServiceApplication.class, args);
    }
}
