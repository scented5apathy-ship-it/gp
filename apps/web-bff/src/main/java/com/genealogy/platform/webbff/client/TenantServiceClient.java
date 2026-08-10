package com.genealogy.platform.webbff.client;

import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Typed {@link RestClient} wrapper around the
 * {@code tenant-service} membership surface. Consumed by the
 * BFF reconciliation layer (E3.5) — the BFF never calls
 * {@code tenant-service} directly; the call goes through this
 * client so we can mock it in unit tests.
 *
 * <p>The base URL is bound from
 * {@code platform.bff.tenant-service.base-url} and the wire
 * format follows the OpenAPI contract under
 * {@code contracts/openapi/public-api/v1/tenant.yaml}.
 */
@Component
public class TenantServiceClient {

    private final RestClient restClient;
    private final String baseUrl;

    public TenantServiceClient(
            RestClient.Builder builder,
            @Value("${platform.bff.tenant-service.base-url:http://tenant-service.gp-services:8080}")
                    String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
    }

    /**
     * List the memberships for a tenant. The {@code subject} is
     * forwarded as a service-to-service caller header so
     * tenant-service can return the rows it owns.
     *
     * @param tenantId opaque tenant id
     * @param subject Keycloak subject (the BFF has already
     *                validated the JWT signature)
     * @param correlationId propagated for trace correlation
     */
    public MembershipView.Page listMemberships(String tenantId, String subject, String correlationId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(subject, "subject");
        try {
            return restClient.get()
                    .uri(baseUrl + "/api/v1/tenants/{tenantId}/memberships", tenantId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + subject)
                    .header("X-Correlation-Id", correlationId == null ? "" : correlationId)
                    .retrieve()
                    .body(MembershipView.Page.class);
        } catch (RestClientException ex) {
            // The tenant-service may legitimately return 404
            // (cross-tenant access); translate to empty page so
            // the reconciler can decide whether the result
            // represents "no membership" or "no access".
            return null;
        }
    }

    public String baseUrl() {
        return baseUrl;
    }
}
