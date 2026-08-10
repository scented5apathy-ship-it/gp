package com.genealogy.platform.webbff.reconcile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.genealogy.platform.webbff.client.MembershipView;
import com.genealogy.platform.webbff.client.TenantServiceClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MembershipReconcilerTest {

    private TenantServiceClient client;
    private MembershipReconciler reconciler;

    @BeforeEach
    void setUp() {
        client = mock(TenantServiceClient.class);
        reconciler = new MembershipReconciler(client);
    }

    @Test
    void allowedWhenActiveMembershipMatches() {
        MembershipView.Page page = new MembershipView.Page();
        page.items = List.of(membership("tenant-1", "user-1", "MEMBER", "ACTIVE"));
        when(client.listMemberships(eq("tenant-1"), eq("user-1"), any()))
                .thenReturn(page);

        TenantReconciliationResult result = reconciler.reconcile("user-1", "tenant-1", "corr-1");
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.actorRole()).isEqualTo("MEMBER");
        assertThat(result.tenantId()).isEqualTo("tenant-1");
    }

    @Test
    void deniedWhenMembershipIsInvited() {
        MembershipView.Page page = new MembershipView.Page();
        page.items = List.of(membership("tenant-1", "user-1", "MEMBER", "INVITED"));
        when(client.listMemberships(eq("tenant-1"), eq("user-1"), any()))
                .thenReturn(page);

        TenantReconciliationResult result = reconciler.reconcile("user-1", "tenant-1", "corr-1");
        assertThat(result.isAllowed()).isFalse();
        assertThat(result.status()).isEqualTo(TenantReconciliationStatus.MEMBERSHIP_NOT_ACTIVE);
    }

    @Test
    void deniedWhenMembershipIsSuspended() {
        MembershipView.Page page = new MembershipView.Page();
        page.items = List.of(membership("tenant-1", "user-1", "MEMBER", "SUSPENDED"));
        when(client.listMemberships(eq("tenant-1"), eq("user-1"), any()))
                .thenReturn(page);

        TenantReconciliationResult result = reconciler.reconcile("user-1", "tenant-1", "corr-1");
        assertThat(result.status()).isEqualTo(TenantReconciliationStatus.MEMBERSHIP_NOT_ACTIVE);
    }

    @Test
    void deniedWhenMembershipIsRevoked() {
        MembershipView.Page page = new MembershipView.Page();
        page.items = List.of(membership("tenant-1", "user-1", "MEMBER", "REVOKED"));
        when(client.listMemberships(eq("tenant-1"), eq("user-1"), any()))
                .thenReturn(page);

        TenantReconciliationResult result = reconciler.reconcile("user-1", "tenant-1", "corr-1");
        assertThat(result.status()).isEqualTo(TenantReconciliationStatus.MEMBERSHIP_NOT_ACTIVE);
    }

    @Test
    void deniedWhenTenantDoesNotMatch() {
        MembershipView.Page page = new MembershipView.Page();
        page.items = List.of(membership("tenant-2", "user-1", "MEMBER", "ACTIVE"));
        when(client.listMemberships(eq("tenant-1"), eq("user-1"), any()))
                .thenReturn(page);

        TenantReconciliationResult result = reconciler.reconcile("user-1", "tenant-1", "corr-1");
        assertThat(result.status()).isEqualTo(TenantReconciliationStatus.TENANT_NOT_FOUND);
    }

    @Test
    void deniedWhenSubjectDoesNotMatch() {
        MembershipView.Page page = new MembershipView.Page();
        page.items = List.of(membership("tenant-1", "user-OTHER", "MEMBER", "ACTIVE"));
        when(client.listMemberships(eq("tenant-1"), eq("user-1"), any()))
                .thenReturn(page);

        TenantReconciliationResult result = reconciler.reconcile("user-1", "tenant-1", "corr-1");
        assertThat(result.status()).isEqualTo(TenantReconciliationStatus.TENANT_NOT_FOUND);
    }

    @Test
    void deniedWhenPageIsEmpty() {
        MembershipView.Page page = new MembershipView.Page();
        page.items = List.of();
        when(client.listMemberships(eq("tenant-1"), eq("user-1"), any()))
                .thenReturn(page);

        TenantReconciliationResult result = reconciler.reconcile("user-1", "tenant-1", "corr-1");
        assertThat(result.status()).isEqualTo(TenantReconciliationStatus.TENANT_NOT_FOUND);
    }

    @Test
    void deniedWhenClientReturnsNull() {
        when(client.listMemberships(any(), any(), any())).thenReturn(null);

        TenantReconciliationResult result = reconciler.reconcile("user-1", "tenant-1", "corr-1");
        assertThat(result.status()).isEqualTo(TenantReconciliationStatus.TENANT_NOT_FOUND);
    }

    @Test
    void deniedWhenClientThrows() {
        when(client.listMemberships(any(), any(), any()))
                .thenThrow(new RuntimeException("tenant-service down"));

        TenantReconciliationResult result = reconciler.reconcile("user-1", "tenant-1", "corr-1");
        assertThat(result.status()).isEqualTo(TenantReconciliationStatus.TENANT_NOT_FOUND);
    }

    @Test
    void deniedWhenSubjectMissing() {
        TenantReconciliationResult result = reconciler.reconcile(null, "tenant-1", "corr-1");
        assertThat(result.status()).isEqualTo(TenantReconciliationStatus.SUBJECT_MISSING);
    }

    @Test
    void deniedWhenTenantSelectionBlank() {
        TenantReconciliationResult result = reconciler.reconcile("user-1", "", "corr-1");
        assertThat(result.status()).isEqualTo(TenantReconciliationStatus.TENANT_NOT_FOUND);
    }

    private static MembershipView membership(
            String tenantId, String userId, String role, String status) {
        return new MembershipView(
                "m-" + tenantId + "-" + userId,
                tenantId, userId, role, status, null, null);
    }
}
