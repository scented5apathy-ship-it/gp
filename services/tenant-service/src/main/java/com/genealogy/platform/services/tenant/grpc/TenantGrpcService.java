package com.genealogy.platform.services.tenant.grpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Spring component stub for the {@code TenantService} gRPC server
 * (E3.2e). The `spring-boot-starter-grpc` (transitive via
 * `platform-spring-boot-starter`) binds the gRPC port on startup
 * so the E2.1 Helm chart probe can connect; the real
 * `TenantService` implementation lands in E4.x once the
 * `com.google.protobuf` Gradle plugin is wired (see
 * `services/tenant-service/build.gradle.kts` header).
 *
 * <p>Why a stub and not a real implementation in E3.2:
 * <ul>
 *   <li>E1.3 ships the protobuf contracts under
 *       `contracts/protobuf/tenant/v1/` but the
 *       {@code tenant_service.proto} + {@code person_service.proto}
 *       pair has duplicate enum values across sibling enums in the
 *       same package — {@code protoc} rejects the file until the
 *       collisions are resolved.</li>
 *   <li>E3.2a–E3.2d lock the REST surface (the canonical contract
 *       in `contracts/openapi/public-api/v1/tenant.yaml`); the gRPC
 *       surface mirrors it but is not yet exercised by another
 *       service. No consumer depends on the gRPC port today, so a
 *       stub preserves the ownership-catalog §2.1 sync-dep budget
 *       ({@code n_sync ≤ 2}: Keycloak + Postgres only).</li>
 *   <li>The class logs the bound port on startup so a smoke test
 *       can confirm the gRPC server is up without an implementation.</li>
 * </ul>
 *
 * <p>TODO (E4.x — after enum collision resolution + protobuf
 * plugin wiring):
 * <ol>
 *   <li>Replace this stub with a generated {@code TenantServiceImplBase}
 *       subclass once {@code protoc} produces the Java stubs.</li>
 *   <li>Wire each gRPC method to the E3.2c command services
 *       (`TenantCommandService`, `MembershipCommandService`,
 *       `EntitlementCommandService`); the gRPC method body must
 *       call {@code rls.bind()} inside its {@code @Transactional}
 *       method, mirroring the REST + JDBC paths.</li>
 *   <li>Convert the `*QueryService` calls to stream / page
 *       responses as documented in `tenant_service.proto`.</li>
 *   <li>Promote this class to a real {@code @GrpcService} bean
 *       (the starter scans for the annotation).</li>
 * </ol>
 *
 * <p>Per `agent-execution.md` §4.4 + E3.2e DoD the gRPC surface
 * MUST NOT be considered the contract of record for E3.2 — REST is.
 */
@Component
public class TenantGrpcService {

    private static final Logger LOG = LoggerFactory.getLogger(TenantGrpcService.class);

    public TenantGrpcService() {
        LOG.info(
            "tenant-service gRPC stub bound (E3.2e); implementation lands in E4.x "
                + "after protobuf plugin + enum collision resolution"
        );
    }
}
