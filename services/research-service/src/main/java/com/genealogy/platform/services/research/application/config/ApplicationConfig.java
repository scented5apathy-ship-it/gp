package com.genealogy.platform.services.research.application.config;

import com.genealogy.platform.services.research.application.audit.ResearchAuditPublisher;
import com.genealogy.platform.services.research.application.persistence.CitationRepository;
import com.genealogy.platform.services.research.application.persistence.ConflictRepository;
import com.genealogy.platform.services.research.application.persistence.HypothesisRepository;
import com.genealogy.platform.services.research.application.persistence.ProvenanceJdbcRepository;
import com.genealogy.platform.services.research.application.persistence.RepositoryRepository;
import com.genealogy.platform.services.research.application.persistence.ResearchTaskRepository;
import com.genealogy.platform.services.research.application.persistence.SourceRepository;
import com.genealogy.platform.services.research.domain.ids.IdGenerator;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Spring wiring for the E6.1c application layer.
 *
 * <p>Every bean in this class is replaceable: the UUID v4 id
 * generator wraps {@link UUID#randomUUID()} (which the JVM seeds
 * with a strong entropy source), the system {@link Clock} can be
 * swapped to a fixed clock in unit tests, and the audit publisher
 * delegates to the platform-managed {@code AuditPublisher}
 * interface so the dedicated Kafka / audit-service transport
 * (E6.1d) can plug in without a code change.
 */
@Configuration
public class ApplicationConfig {

    /**
     * UUID v4 generator. The runtime guarantees uniqueness by
     * delegating to {@link UUID#randomUUID()} which the JVM seeds
     * with a strong entropy source. Unit tests override this bean
     * with a deterministic counter-based implementation.
     */
    @Bean
    public IdGenerator uuidV4IdGenerator() {
        return () -> UUID.randomUUID().toString();
    }

    /**
     * System UTC clock. Unit tests override this bean with
     * {@link Clock#fixed} so the aggregate timestamps are
     * deterministic.
     */
    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    public ResearchAuditPublisher researchAuditPublisher(
            com.genealogy.platform.spring.audit.AuditPublisher publisher) {
        return new ResearchAuditPublisher(publisher);
    }

    @Bean
    public RepositoryRepository repositoryRepository(JdbcTemplate jdbc, Clock clock) {
        return new RepositoryRepository(jdbc, clock);
    }

    @Bean
    public SourceRepository sourceRepository(JdbcTemplate jdbc, Clock clock) {
        return new SourceRepository(jdbc, clock);
    }

    @Bean
    public CitationRepository citationRepository(JdbcTemplate jdbc, Clock clock) {
        return new CitationRepository(jdbc, clock);
    }

    @Bean
    public ResearchTaskRepository researchTaskRepository(JdbcTemplate jdbc, Clock clock) {
        return new ResearchTaskRepository(jdbc, clock);
    }

    @Bean
    public HypothesisRepository hypothesisRepository(JdbcTemplate jdbc, Clock clock) {
        return new HypothesisRepository(jdbc, clock);
    }

    @Bean
    public ConflictRepository conflictRepository(JdbcTemplate jdbc, Clock clock) {
        return new ConflictRepository(jdbc, clock);
    }

    @Bean
    public ProvenanceJdbcRepository provenanceJdbcRepository(JdbcTemplate jdbc, Clock clock) {
        return new ProvenanceJdbcRepository(jdbc, clock);
    }
}
