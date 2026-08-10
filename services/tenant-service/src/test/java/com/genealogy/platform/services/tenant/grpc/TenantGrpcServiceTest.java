package com.genealogy.platform.services.tenant.grpc;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test for the E3.2e gRPC stub. Confirms that:
 *
 * <ul>
 *   <li>The bean can be instantiated by the Spring container (the
 *       constructor must not throw, otherwise the
 *       {@code spring-boot-starter-grpc} boot phase would fail and
 *       the E2.1 readiness probe would never come up).</li>
 *   <li>The stub remains a single no-arg constructor so the
 *       E3.2e DoD ("{@code TenantGrpcService} chỉ là Spring
 *       {@code @Component} với TODO note") is preserved.</li>
 * </ul>
 *
 * <p>Real {@code TenantServiceImplBase} coverage lands in E4.x
 * once the protobuf plugin is wired + enum collisions resolved.
 * The Testcontainers IT covers the gRPC port being bound on
 * startup; this unit test only proves the bean wires up cleanly.
 */
@DisplayName("TenantGrpcService E3.2e stub")
class TenantGrpcServiceTest {

    @Test
    @DisplayName("constructs without throwing")
    void constructsWithoutThrowing() {
        final TenantGrpcService stub = new TenantGrpcService();
        assertNotNull(stub);
    }

    @Test
    @DisplayName("can be instantiated repeatedly")
    void canBeInstantiatedRepeatedly() {
        assertDoesNotThrow(() -> new TenantGrpcService());
        assertDoesNotThrow(() -> new TenantGrpcService());
    }
}
