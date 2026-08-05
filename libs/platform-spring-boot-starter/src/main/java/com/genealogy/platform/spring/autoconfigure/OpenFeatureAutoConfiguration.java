package com.genealogy.platform.spring.autoconfigure;

import com.genealogy.platform.spring.featureflags.SafeFeatureClient;
import dev.openfeature.sdk.NoOpProvider;
import dev.openfeature.sdk.OpenFeatureAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Wires the OpenFeature SDK + a safe in-memory fallback provider.
 *
 * <p>The service starts even when Flagsmith is unreachable: a
 * {@link NoOpProvider} is installed as the default so every flag
 * evaluation returns the default value passed to
 * {@link SafeFeatureClient#getBoolean(String, boolean)} /
 * {@link SafeFeatureClient#getString(String, String)}. The Flagsmith
 * provider is registered only when
 * {@code platform.openfeature.provider=flagsmith} AND
 * {@code platform.openfeature.flagsmith-base-url} is set. The
 * provider itself is not added in E1.4 — the API contract is in
 * place so a future E2.8 lands the actual provider with no
 * consumer changes.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PlatformProperties.class)
@AutoConfigureAfter(AuditAutoConfiguration.class)
public class OpenFeatureAutoConfiguration implements DisposableBean {

    private static final Logger LOG = LoggerFactory.getLogger(OpenFeatureAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public OpenFeatureAPI openFeatureAPI(PlatformProperties properties) {
        OpenFeatureAPI api = OpenFeatureAPI.getInstance();
        // NoOpProvider is always installed as a safe default. A real
        // provider (Flagsmith) is installed by E2.8 in the same
        // conditional branch that detects the resolved
        // platform.openfeature.provider property.
        api.setProviderAndWait(new NoOpProvider());
        LOG.info(
                "OpenFeature ready: requested_provider={} safe_fallback=noop",
                properties.getOpenfeature().getProvider());
        return api;
    }

    @Bean
    @ConditionalOnMissingBean
    public SafeFeatureClient safeFeatureClient(OpenFeatureAPI api, Environment environment) {
        String serviceName = environment.getProperty("spring.application.name", "unknown-service");
        return new SafeFeatureClient(api, serviceName);
    }

    @Override
    public void destroy() {
        // Shutdown the OpenFeature client gracefully so the in-process
        // provider (when wired in E2.8) flushes its evaluation cache.
        try {
            OpenFeatureAPI.getInstance().shutdown();
        } catch (RuntimeException ex) {
            LOG.warn("OpenFeature shutdown failed: {}", ex.getMessage());
        }
    }
}
