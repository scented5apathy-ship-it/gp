/*
 * Unit tests for the E1.4 Spring Boot auto-configuration. Verifies
 * the safe defaults that every service inherits by depending on
 * `platform-spring-boot-starter`:
 *
 *   - trusted tenant context is required by default
 *   - audit counter starts at 0 and increments on every event
 *   - OpenFeature safe fallback returns the default value
 *   - properties are bound from the `platform.*` prefix
 */
package com.genealogy.platform.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.genealogy.platform.spring.autoconfigure.PlatformProperties;
import com.genealogy.platform.spring.audit.AuditEvent;
import com.genealogy.platform.spring.audit.AuditPublisher;
import com.genealogy.platform.spring.audit.MicrometerAuditPublisher;
import com.genealogy.platform.spring.context.TrustedTenantContext;
import com.genealogy.platform.spring.featureflags.SafeFeatureClient;
import dev.openfeature.sdk.OpenFeatureAPI;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlatformSpringBootStarterTest {

    @Test
    void platformPropertiesDefaultToSecureValues() {
        PlatformProperties props = new PlatformProperties();
        assertThat(props.getTenant().isHeaderRequired()).isTrue();
        assertThat(props.getTenant().getMaxIdLength()).isEqualTo(64);
        assertThat(props.getSecurity().isRequireBearerToken()).isTrue();
        assertThat(props.getAudit().isEnabled()).isTrue();
        assertThat(props.getOpenfeature().getProvider()).isEqualTo("noop");
        assertThat(props.getOpenfeature().getCacheTtlMillis()).isEqualTo(30_000L);
        assertThat(props.getShutdown().getTimeoutSeconds()).isEqualTo(30L);
        assertThat(props.getOtel().isAlwaysSample()).isFalse();
    }

    @Test
    void auditPublisherIncrementsCounter() {
        MeterRegistry registry = new SimpleMeterRegistry();
        PlatformProperties props = new PlatformProperties();
        AuditPublisher publisher = new MicrometerAuditPublisher(registry, props);

        publisher.publish(new AuditEvent(
                "tenant-1", "actor-1", "tenant.create", "tenant", "smith",
                "corr-1", Map.of("display_name", "Smith")));
        publisher.publish(new AuditEvent(
                "tenant-1", "actor-1", "tenant.update", "tenant", "smith",
                "corr-2", Map.of()));

        double count = registry.find("platform.audit.events").counter().count();
        assertThat(count).isEqualTo(2d);
    }

    @Test
    void auditPublisherCanBeDisabled() {
        MeterRegistry registry = new SimpleMeterRegistry();
        PlatformProperties props = new PlatformProperties();
        props.getAudit().setEnabled(false);
        AuditPublisher publisher = new MicrometerAuditPublisher(registry, props);

        publisher.publish(new AuditEvent(
                "tenant-1", "actor-1", "tenant.create", "tenant", "smith",
                "corr-1", Map.of()));

        double count = registry.find("platform.audit.events").counter().count();
        assertThat(count).isEqualTo(0d);
    }

    @Test
    void trustedTenantContextDefaultsAreSafe() {
        TrustedTenantContext empty = TrustedTenantContext.empty();
        assertThat(empty.isAuthenticated()).isFalse();
        assertThat(empty.getTenantId()).isNull();
        assertThat(empty.getCorrelationId()).isNull();

        TrustedTenantContext ctx = TrustedTenantContext.of("tenant-1", "actor-1", "MEMBER", "corr-1", "trace-1");
        assertThat(ctx.isAuthenticated()).isTrue();
        assertThat(ctx.getTenantId()).isEqualTo("tenant-1");
        assertThat(ctx.getActorId()).isEqualTo("actor-1");
        assertThat(ctx.getActorRole()).isEqualTo("MEMBER");
        assertThat(ctx.getCorrelationId()).isEqualTo("corr-1");
    }

    @Test
    void safeFeatureClientReturnsDefaultsWhenProviderIsNoop() {
        OpenFeatureAPI api = OpenFeatureAPI.getInstance();
        api.setProviderAndWait(new dev.openfeature.sdk.NoOpProvider());
        SafeFeatureClient client = new SafeFeatureClient(api, "tenant-service");

        // No flags are registered; the NoOpProvider returns the
        // default value, so the safe client surfaces the default.
        assertThat(client.getBoolean("missing.flag", false)).isFalse();
        assertThat(client.getBoolean("missing.flag", true)).isTrue();
        assertThat(client.getString("missing.flag", "fallback")).isEqualTo("fallback");
    }
}
