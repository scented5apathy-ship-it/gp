package com.genealogy.platform.spring.autoconfigure;

import com.genealogy.platform.spring.grpc.GrpcTrustedContextInterceptor;
import io.grpc.ServerInterceptor;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.server.GlobalServerInterceptor;

/**
 * Wires the {@link GrpcTrustedContextInterceptor} as a Spring
 * gRPC global server interceptor. Per {@code design.md} §6.1 /
 * §7.2 + E3.5 every service that exposes a gRPC port MUST install
 * this interceptor — it is the single point that:
 *
 * <ul>
 *   <li>reads the BFF-signed {@code x-tenant-id} /
 *       {@code x-actor-id} / {@code x-actor-role} /
 *       {@code x-correlation-id} metadata;</li>
 *   <li>validates the Istio mTLS SPIFFE peer identity against
 *       the sanctioned trust zone;</li>
 *   <li>populates the thread-local
 *       {@link com.genealogy.platform.spring.context.TrustedTenantContext}
 *       for the duration of the call.</li>
 * </ul>
 *
 * <p>The auto-configuration is only active when Spring gRPC is
 * on the classpath ({@code spring-grpc-spring-boot-starter},
 * pulled in transitively by {@code platform-spring-boot-starter}).
 * Services that do not expose a gRPC port (e.g. the public-api)
 * simply do not register the bean.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = {
        "io.grpc.ServerInterceptor",
        "org.springframework.grpc.server.GlobalServerInterceptor"
})
public class GrpcTrustedContextAutoConfiguration {

    /**
     * The default SPIFFE pattern for sanctioned BFF callers; can
     * be overridden by registering a custom
     * {@code com.genealogy.platform.spring.grpc.TrustedContextReconstructor}.
     */
    @Bean
    @ConditionalOnMissingBean(name = "trustedContextReconstructor")
    public com.genealogy.platform.spring.grpc.TrustedContextReconstructor trustedContextReconstructor() {
        return new com.genealogy.platform.spring.grpc.TrustedContextReconstructor();
    }

    @Bean
    @ConditionalOnMissingBean(name = "grpcTrustedContextInterceptor")
    @ConditionalOnBean(com.genealogy.platform.spring.grpc.TrustedContextReconstructor.class)
    public GrpcTrustedContextInterceptor grpcTrustedContextInterceptor(
            com.genealogy.platform.spring.grpc.TrustedContextReconstructor reconstructor) {
        Objects.requireNonNull(reconstructor, "reconstructor");
        return new GrpcTrustedContextInterceptor(reconstructor);
    }

    /**
     * Register the interceptor as a Spring gRPC global interceptor
     * so every gRPC service picks it up automatically. The
     * {@link GlobalServerInterceptor} annotation is the Spring
     * gRPC contract for this purpose.
     */
    @Bean
    @ConditionalOnMissingBean(name = "globalGrpcTrustedContextInterceptor")
    @ConditionalOnBean(GrpcTrustedContextInterceptor.class)
    @GlobalServerInterceptor
    public ServerInterceptor globalGrpcTrustedContextInterceptor(
            GrpcTrustedContextInterceptor interceptor) {
        return interceptor;
    }
}
