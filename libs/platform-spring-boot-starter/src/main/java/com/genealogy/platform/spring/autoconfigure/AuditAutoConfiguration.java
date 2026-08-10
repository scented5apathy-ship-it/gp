package com.genealogy.platform.spring.autoconfigure;

import com.genealogy.platform.spring.audit.AuditEventSink;
import com.genealogy.platform.spring.audit.AuditEventValidator;
import com.genealogy.platform.spring.audit.AuditPublisher;
import com.genealogy.platform.spring.audit.AuditRedactor;
import com.genealogy.platform.spring.audit.KafkaAuditPublisher;
import com.genealogy.platform.spring.audit.MicrometerAuditPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the default {@link AuditPublisher}. Selection order:
 *
 * <ol>
 *   <li>If an {@link AuditEventSink} bean exists (E3.6 onwards the
 *       Kafka sink is provided by the service that owns the topic),
 *       bind {@link KafkaAuditPublisher} so every service that
 *       depends on the starter inherits the validator +
 *       redactor + sink pipeline.
 *   <li>Otherwise fall back to the E1.4
 *       {@code MicrometerAuditPublisher} (counter + structured log)
 *       so services that have not yet wired Kafka still emit the
 *       metric.
 *   <li>Disable by setting {@code platform.audit.enabled=false} —
 *       the publisher becomes a no-op (sink.send is never
 *       invoked).
 * </ol>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PlatformProperties.class)
@ConditionalOnProperty(prefix = "platform.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuditEventSink auditEventSink() {
        return AuditEventSink.NOOP;
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditEventValidator auditEventValidator() {
        return new AuditEventValidator();
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditRedactor auditRedactor() {
        return AuditRedactor.defaultRedactor();
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditPublisher auditPublisher(
            AuditEventSink auditEventSink,
            AuditEventValidator auditEventValidator,
            AuditRedactor auditRedactor,
            MeterRegistry meterRegistry,
            PlatformProperties properties) {
        if (auditEventSink == AuditEventSink.NOOP) {
            return new MicrometerAuditPublisher(meterRegistry, properties);
        }
        return new KafkaAuditPublisher(
                auditEventSink, auditEventValidator, auditRedactor, meterRegistry, properties);
    }
}
