package com.genealogy.platform.services.audit.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genealogy.platform.services.audit.export.ExportService;
import com.genealogy.platform.services.audit.ingest.AuditIngestService;
import com.genealogy.platform.services.audit.integrity.IntegrityVerifier;
import com.genealogy.platform.services.audit.persistence.AuditEntryRepository;
import com.genealogy.platform.services.audit.persistence.JdbcAuditEntryRepository;
import com.genealogy.platform.services.audit.retention.RetentionSweeper;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring wiring for the audit-service ledger. Everything is
 * interface-typed so the test path can swap the JDBC repository
 * for an in-memory variant without touching the application code.
 */
@Configuration(proxyBeanMethods = false)
public class ApplicationConfig {

    @Bean
    public AuditEntryRepository auditEntryRepository(DataSource dataSource, ObjectMapper objectMapper) {
        return new JdbcAuditEntryRepository(dataSource, objectMapper);
    }

    @Bean
    public AuditIngestService auditIngestService(AuditEntryRepository repository) {
        return new AuditIngestService(repository);
    }

    @Bean
    public IntegrityVerifier integrityVerifier(AuditEntryRepository repository) {
        return new IntegrityVerifier(repository);
    }

    @Bean
    public RetentionSweeper.LegalHoldProbe legalHoldProbe() {
        // E3.6 placeholder: a real implementation reads from the
        // platform legal-hold service (out of scope for this epic).
        return tenantId -> false;
    }

    @Bean
    public RetentionSweeper retentionSweeper(
            AuditEntryRepository repository,
            DataSource dataSource,
            RetentionSweeper.LegalHoldProbe legalHoldProbe) {
        java.util.Map<String, java.time.Duration> hotDays = new java.util.LinkedHashMap<>();
        hotDays.put("auth", java.time.Duration.ofDays(365));
        hotDays.put("authorization", java.time.Duration.ofDays(365));
        hotDays.put("policy", java.time.Duration.ofDays(730));
        hotDays.put("support", java.time.Duration.ofDays(730));
        hotDays.put("download", java.time.Duration.ofDays(730));
        hotDays.put("consent", java.time.Duration.ofDays(1825));
        return new RetentionSweeper(
                repository,
                dataSource,
                new RetentionSweeper.RetentionPolicy(hotDays, RetentionSweeper.LegalHoldMode.HARD_BLOCK),
                legalHoldProbe);
    }

    @Bean
    public ExportService.BundleSigner bundleSigner() {
        return manifest -> java.util.UUID.randomUUID().toString();
    }

    @Bean
    public ExportService exportService(
            AuditEntryRepository repository,
            IntegrityVerifier integrityVerifier,
            ExportService.BundleSigner signer) {
        return new ExportService(repository, integrityVerifier, signer);
    }
}
