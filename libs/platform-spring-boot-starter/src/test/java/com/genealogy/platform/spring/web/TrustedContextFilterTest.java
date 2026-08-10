package com.genealogy.platform.spring.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class TrustedContextFilterTest {

    @Test
    void rejectsClientSuppliedTenantIdInQuery() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setQueryString("tenantId=acme");
        Map<String, String[]> params = new HashMap<>();
        params.put("tenantId", new String[] {"acme"});
        req.setParameters(params);
        String reject = TrustedContextFilter.rejectClientSuppliedIdentity(req);
        assertThat(reject).isEqualTo("tenantId");
    }

    @Test
    void rejectsClientSuppliedRoleInQuery() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setQueryString("role=OWNER");
        req.setParameters(Map.of("role", new String[] {"OWNER"}));
        assertThat(TrustedContextFilter.rejectClientSuppliedIdentity(req)).isEqualTo("role");
    }

    @Test
    void rejectsClientSuppliedActorRoleCamelCase() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setQueryString("actor_role=ADMIN");
        req.setParameters(Map.of("actor_role", new String[] {"ADMIN"}));
        assertThat(TrustedContextFilter.rejectClientSuppliedIdentity(req)).isEqualTo("actor_role");
    }

    @Test
    void rejectsClientSuppliedSubject() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setQueryString("subject=user-1");
        req.setParameters(Map.of("subject", new String[] {"user-1"}));
        assertThat(TrustedContextFilter.rejectClientSuppliedIdentity(req)).isEqualTo("subject");
    }

    @Test
    void allowsAbsenceOfForbiddenParams() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setQueryString("page=1&size=20");
        req.setParameters(Map.of(
                "page", new String[] {"1"},
                "size", new String[] {"20"}));
        assertThat(TrustedContextFilter.rejectClientSuppliedIdentity(req)).isNull();
    }

    @Test
    void allowsEmptyForbiddenParam() {
        // Spring sets a `?tenantId=` parameter to [""] which is
        // an empty string and must NOT be treated as a client-
        // supplied identity.
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setQueryString("tenantId=");
        req.setParameters(Map.of("tenantId", new String[] {""}));
        assertThat(TrustedContextFilter.rejectClientSuppliedIdentity(req)).isNull();
    }

    @Test
    void handlesNoQueryString() {
        HttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/tenants");
        assertThat(TrustedContextFilter.rejectClientSuppliedIdentity(req)).isNull();
        // The mock servlet request can return null from getQueryString(); guard.
        assertThat(Collections.unmodifiableMap(Map.of("k", "v"))).isNotNull();
    }
}
