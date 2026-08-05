package com.genealogy.platform.spring.autoconfigure;

import com.genealogy.platform.spring.web.TrustedContextFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers the {@link TrustedContextFilter} as a servlet filter at
 * the highest precedence so every authenticated request populates
 * the thread-local {@code TrustedTenantContext} before any other
 * filter (security, audit) reads it.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PlatformProperties.class)
public class TrustedContextAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FilterRegistrationBean<TrustedContextFilter> trustedContextFilter(PlatformProperties properties) {
        FilterRegistrationBean<TrustedContextFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new TrustedContextFilter(properties));
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        reg.addUrlPatterns("/*");
        reg.setName("trustedContextFilter");
        return reg;
    }
}
