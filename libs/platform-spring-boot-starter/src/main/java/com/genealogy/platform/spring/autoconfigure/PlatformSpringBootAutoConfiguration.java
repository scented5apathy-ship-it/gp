package com.genealogy.platform.spring.autoconfigure;

import com.genealogy.platform.spring.featureflags.SafeFeatureClient;
import com.genealogy.platform.spring.web.TrustedContextFilter;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Top-level auto-configuration that pulls in every piece of the
 * shared Spring Boot wiring for the Genealogy Platform monorepo.
 *
 * <p>Spring Boot picks this up via
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * and applies it the moment {@code platform-spring-boot-starter} is
 * on the classpath — services only have to depend on the starter
 * and provide their own {@code application.yml} overrides.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PlatformProperties.class)
@ComponentScan(
        basePackageClasses = {
            PlatformProperties.class,
            TrustedContextFilter.class,
            SafeFeatureClient.class,
            AuditAutoConfiguration.class,
            OpenFeatureAutoConfiguration.class,
            TrustedContextAutoConfiguration.class
        })
public class PlatformSpringBootAutoConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformSpringBootAutoConfiguration.class);

    @PreDestroy
    public void onShutdown() {
        LOG.info("Platform Spring Boot auto-configuration shutting down gracefully");
    }
}
