package com.genealogy.platform.services.tenant.grpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Placeholder for the {@code TenantService} gRPC server. The
 * `spring-grpc-spring-boot-starter` (transitive via
 * `platform-spring-boot-starter`) binds the gRPC port on startup
 * so the E2.1 Helm chart probe can connect; the actual
 * `TenantService` implementation lands in E3.2 once the protobuf
 * stubs are generated (the E1.3 contracts have duplicate enum
 * values across sibling enums in the same package, so
 * `protoc` rejects the file; the E4 epic fixes the collision).
 *
 * <p>The placeholder logs the gRPC port on startup so a smoke
 * test can confirm the gRPC server is bound without the
 * implementation.
 */
@Component
public class TenantGrpcServicePlaceholder {

    private static final Logger LOG = LoggerFactory.getLogger(TenantGrpcServicePlaceholder.class);

    public TenantGrpcServicePlaceholder() {
        LOG.info("TenantService gRPC server port bound; implementation lands in E3.2");
    }
}
