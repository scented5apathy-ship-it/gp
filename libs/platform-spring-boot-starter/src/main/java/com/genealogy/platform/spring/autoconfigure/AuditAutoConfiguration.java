package com.genealogy.platform.spring.autoconfigure;

import com.genealogy.platform.spring.audit.AuditPublisher;
import com.genealogy.platform.spring.audit.MicrometerAuditPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the default {@link AuditPublisher} (Micrometer counter +
 * structured log) unless the service has supplied its own bean
 * (E3.6 will register a Kafka-backed publisher for services that
 * need to forward events to the dedicated audit-service).
 *
 * <p>Disable by setting {@code platform.audit.enabled=false} — the
 * bean still exists so DI consumers compile, but the implementation
 * becomes a no-op.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PlatformProperties.class)
@ConditionalOnProperty(prefix = "platform.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuditPublisher auditPublisher(MeterRegistry meterRegistry, PlatformProperties properties) {
        return new MicrometerAuditPublisher(meterRegistry, properties);
    }
}
