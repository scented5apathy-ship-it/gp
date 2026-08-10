package com.genealogy.platform.services.tenant.application;

import com.genealogy.platform.libs.security.abac.Jurisdiction;
import com.genealogy.platform.services.tenant.application.audit.TenantAuditPublisher;
import com.genealogy.platform.services.tenant.application.keycloak.KeycloakSubjectMirror;
import com.genealogy.platform.services.tenant.application.outbox.EventPayloads;
import com.genealogy.platform.services.tenant.application.outbox.OutboxEvent;
import com.genealogy.platform.services.tenant.application.outbox.OutboxWriter;
import com.genealogy.platform.services.tenant.application.persistence.InvitationRepository;
import com.genealogy.platform.services.tenant.application.persistence.MembershipRepository;
import com.genealogy.platform.services.tenant.application.persistence.TenantRepository;
import com.genealogy.platform.services.tenant.application.rls.TenantRlsTxInterceptor;
import com.genealogy.platform.services.tenant.domain.ids.IdGenerator;
import com.genealogy.platform.services.tenant.domain.ids.TenantId;
import com.genealogy.platform.services.tenant.domain.ids.UserId;
import com.genealogy.platform.services.tenant.domain.invitation.Invitation;
import com.genealogy.platform.services.tenant.domain.invitation.TokenHash;
import com.genealogy.platform.services.tenant.domain.membership.Membership;
import com.genealogy.platform.services.tenant.domain.membership.MembershipStatus;
import com.genealogy.platform.services.tenant.domain.tenant.Tenant;
import com.genealogy.platform.services.tenant.spring.context.OutboxCorrelationContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Membership lifecycle: invite, activate, revoke, change role.
 *
 * <p>Membership events are emitted in the same transaction as the
 * aggregate write so the downstream notification-service can send
 * the invite email without losing the row to a crash.
 */
@Service
public class MembershipCommandService {

    private final MembershipRepository membershipRepository;
    private final InvitationRepository invitationRepository;
    private final TenantRepository tenantRepository;
    private final KeycloakSubjectMirror keycloak;
    private final OutboxWriter outboxWriter;
    private final IdGenerator idGenerator;
    private final TokenHasher tokenHasher;
    private final TenantAuditPublisher audit;
    private final TenantRlsTxInterceptor rls;
    private final TenantAbacEnforcer abac;
    private final java.time.Clock clock;

    public MembershipCommandService(
            MembershipRepository membershipRepository,
            InvitationRepository invitationRepository,
            TenantRepository tenantRepository,
            KeycloakSubjectMirror keycloak,
            OutboxWriter outboxWriter,
            IdGenerator idGenerator,
            TokenHasher tokenHasher,
            TenantAuditPublisher audit,
            TenantRlsTxInterceptor rls,
            TenantAbacEnforcer abac,
            java.time.Clock clock) {
        this.membershipRepository =
                Objects.requireNonNull(membershipRepository, "membershipRepository");
        this.invitationRepository =
                Objects.requireNonNull(invitationRepository, "invitationRepository");
        this.tenantRepository = Objects.requireNonNull(tenantRepository, "tenantRepository");
        this.keycloak = Objects.requireNonNull(keycloak, "keycloak");
        this.outboxWriter = Objects.requireNonNull(outboxWriter, "outboxWriter");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
        this.tokenHasher = Objects.requireNonNull(tokenHasher, "tokenHasher");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.rls = Objects.requireNonNull(rls, "rls");
        this.abac = Objects.requireNonNull(abac, "abac");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public Results.InvitationView invite(Commands.InviteMember cmd) {
        rls.bind();
        validateIdempotencyKey(cmd);
        TenantSnapshot snapshot = loadTenantSnapshot(cmd.tenantId());
        abac.requireAllow(
                abac.membershipRequest(
                        cmd.tenantId().getValue(),
                        cmd.invitedByUserId().getValue(),
                        cmd.role().name(),
                        "invite",
                        false,
                        snapshot.softDeleted(),
                        snapshot.jurisdiction()),
                TenantAbacEnforcer.Actions.MEMBERSHIP_INVITE);

        UserId provisionalUserId = keycloak.ensureForEmail(cmd.email().value());
        Membership membership = Membership.invite(idGenerator, cmd.tenantId(),
                provisionalUserId, cmd.role(), clock);
        membershipRepository.insert(membership);

        TokenHash tokenHash = tokenHasher.hash(cmd.rawInviteToken());
        Invitation invitation = Invitation.create(
                idGenerator,
                cmd.tenantId(),
                cmd.email(),
                cmd.role(),
                tokenHash,
                cmd.idempotencyKey(),
                cmd.invitedByUserId(),
                cmd.ttl() == null ? Invitation.DEFAULT_TTL : cmd.ttl(),
                clock);
        invitationRepository.insert(invitation);

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("role", cmd.role().name());
        metadata.put("invitationId", invitation.id().getValue());
        audit.publishMembership("membership.invite", cmd.tenantId(),
                membership.id().getValue(), metadata);

        outboxWriter.append(new OutboxEvent(
                cmd.tenantId(),
                "membership",
                membership.id().getValue(),
                EventPayloads.EVENT_TYPE_MEMBERSHIP_INVITED,
                EventPayloads.SCHEMA_MEMBERSHIP_INVITED,
                EventPayloads.encode(EventPayloads.membershipInvited(
                        cmd.tenantId().getValue(),
                        membership.id().getValue(),
                        invitation.id().getValue(),
                        cmd.email().value(),
                        cmd.role().name(),
                        cmd.invitedByUserId().getValue(),
                        tokenHash.value(),
                        invitation.expiresAt(),
                        membership.invitedAt())),
                OutboxCorrelationContext.correlationId(),
                OutboxCorrelationContext.traceId(),
                metadata));

        return new Results.InvitationView(
                invitation.id(),
                invitation.tenantId(),
                invitation.email().value(),
                invitation.role(),
                invitation.expiresAt(),
                invitation.acceptedAt(),
                invitation.revokedAt(),
                cmd.rawInviteToken());
    }

    @Transactional
    public Results.MembershipView activate(Commands.ActivateMembership cmd) {
        rls.bind();
        TenantSnapshot snapshot = loadTenantSnapshot(cmd.tenantId());
        abac.requireAllow(
                abac.membershipRequest(
                        cmd.tenantId().getValue(),
                        cmd.userId().getValue(),
                        "viewer",
                        "activate",
                        false,
                        snapshot.softDeleted(),
                        snapshot.jurisdiction()),
                TenantAbacEnforcer.Actions.MEMBERSHIP_ACTIVATE);

        // Validate the invite token against the mirror. The mirror
        // matches on hashed token (per ADR-E0.5-06 the raw token is
        // never persisted).
        String emailOnInvite = keycloak.findEmailByRawToken(cmd.inviteToken());
        if (emailOnInvite == null) {
            throw new InvalidInviteTokenException(
                    "invite token does not match any pending invitation");
        }

        // The invite flow stored the membership with the provisional
        // userId returned by `Keycloak.ensureForEmail(email)`. The
        // activation looks up the membership via that provisional id,
        // not via the JWT subject — the JWT subject may differ between
        // the invite and the activation if the user was provisioned
        // later (e.g. self-service sign-up).
        UserId provisionalUserId = keycloak.ensureForEmail(emailOnInvite);
        Membership membership = membershipRepository.findByTenantAndUser(
                cmd.tenantId(), provisionalUserId)
                .orElseThrow(() -> new MembershipNotFoundException(
                        "no membership for email " + emailOnInvite
                                + " in tenant " + cmd.tenantId()));

        if (membership.status() != MembershipStatus.INVITED) {
            throw new InvalidMembershipStateException(
                    "membership " + membership.id() + " is not INVITED (status="
                            + membership.status() + ")");
        }

        membership.activate(clock);
        membershipRepository.update(membership);

        // Mark the invitation accepted so a replay cannot re-activate.
        Invitation invitation = invitationRepository
                .findByIdempotencyKey(cmd.tenantId(),
                        "invite:" + emailOnInvite)
                .orElse(null);
        if (invitation != null && invitation.acceptedAt() == null) {
            invitation.markAccepted(clock);
            invitationRepository.markAccepted(invitation);
        }

        audit.publishMembership("membership.activate", cmd.tenantId(),
                membership.id().getValue(),
                Map.of("role", membership.role().name()));

        outboxWriter.append(new OutboxEvent(
                cmd.tenantId(),
                "membership",
                membership.id().getValue(),
                EventPayloads.EVENT_TYPE_MEMBERSHIP_ACTIVATED,
                EventPayloads.SCHEMA_MEMBERSHIP_ACTIVATED,
                EventPayloads.encode(EventPayloads.membershipActivated(
                        cmd.tenantId().getValue(),
                        membership.id().getValue(),
                        cmd.userId().getValue(),
                        membership.role().name(),
                        cmd.userId().getValue(),
                        membership.joinedAt())),
                OutboxCorrelationContext.correlationId(),
                OutboxCorrelationContext.traceId(),
                Map.of()));

        return toView(membership);
    }

    @Transactional
    public Results.MembershipView revoke(Commands.RevokeMembership cmd) {
        rls.bind();
        Membership membership = membershipRepository.findById(cmd.membershipId())
                .orElseThrow(() -> new MembershipNotFoundException(
                        "membership " + cmd.membershipId() + " not found"));
        TenantSnapshot snapshot = loadTenantSnapshot(cmd.tenantId());

        // E3.4 — ABAC overlay on every membership mutation. The
        // default engine denies suspended / soft-deleted tenants
        // (privacy gate §6.4 T-07). The actor here is the trusted
        // caller; E3.5 wires the real context, today we use the
        // audit log actor. Invalidate cached decisions for this
        // membership after the write so the next read reflects the
        // new state (E3.4 acceptance criterion).
        abac.requireAllow(
                abac.membershipRequest(
                        cmd.tenantId().getValue(),
                        com.genealogy.platform.spring.context.TrustedTenantContext.current()
                                .getActorId(),
                        membership.role().name(),
                        membership.id().getValue(),
                        membership.status() == MembershipStatus.SUSPENDED,
                        snapshot.softDeleted(),
                        snapshot.jurisdiction()),
                TenantAbacEnforcer.Actions.MEMBERSHIP_REVOKE);
        if (!membership.tenantId().equals(cmd.tenantId())) {
            // Defence in depth — the @TenantScoped RLS already binds
            // the tenant predicate, but the explicit check catches
            // programming errors before they reach the SQL layer.
            throw new CrossTenantMembershipException(
                    "membership " + membership.id() + " does not belong to tenant "
                            + cmd.tenantId());
        }
        if (membership.version() != cmd.expectedVersion()) {
            throw new OptimisticConcurrencyException(
                    "expected version " + cmd.expectedVersion()
                            + " but membership is at " + membership.version());
        }
        MembershipStatus previous = membership.status();
        membership.revoke(clock);
        membershipRepository.update(membership);

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("previousStatus", previous.name());
        if (cmd.reason() != null) {
            metadata.put("reason", cmd.reason());
        }
        audit.publishMembership("membership.revoke", cmd.tenantId(),
                membership.id().getValue(), metadata);

        outboxWriter.append(new OutboxEvent(
                cmd.tenantId(),
                "membership",
                membership.id().getValue(),
                EventPayloads.EVENT_TYPE_MEMBERSHIP_REVOKED,
                EventPayloads.SCHEMA_MEMBERSHIP_REVOKED,
                EventPayloads.encode(EventPayloads.membershipRevoked(
                        cmd.tenantId().getValue(),
                        membership.id().getValue(),
                        membership.userId().getValue(),
                        previous.name(),
                        cmd.reason(),
                        com.genealogy.platform.spring.context.TrustedTenantContext.current()
                                .getActorId(),
                        membership.revokedAt())),
                OutboxCorrelationContext.correlationId(),
                OutboxCorrelationContext.traceId(),
                metadata));

        // E3.4 — cache invalidation on role/status change. The
        // membership revoke path is the canonical
        // role / policy / consent change event (privacy gate §D-06):
        // any cached ABAC decision scoped to this membership MUST
        // be cleared so the next read reflects the REVOKED status.
        abac.invalidateOnChange(
                cmd.tenantId().getValue(),
                "membership",
                membership.id().getValue());

        return toView(membership);
    }

    /**
     * Lightweight snapshot of the tenant row used to feed the ABAC
     * enforcer. Kept inside the command service to avoid pulling
     * the {@code Tenant} aggregate into the membership path when
     * a single boolean + jurisdiction is enough.
     */
    private TenantSnapshot loadTenantSnapshot(TenantId tenantId) {
        return tenantRepository.findById(tenantId)
                .map(t -> new TenantSnapshot(
                        t.status() == com.genealogy.platform.services.tenant.domain.tenant.TenantStatus.DELETED,
                        resolveJurisdiction(t)))
                .orElseGet(() -> new TenantSnapshot(false, Jurisdiction.ROW));
    }

    private static Jurisdiction resolveJurisdiction(Tenant tenant) {
        // The Tenant aggregate does not yet carry a jurisdiction
        // column (E0.5-04 closes that ADR). Default to ROW until
        // the tenant row carries the residency cluster; the engine
        // still applies the suspended / soft-deleted rules
        // (privacy gate §6.4 T-07).
        return Jurisdiction.ROW;
    }

    private record TenantSnapshot(boolean softDeleted, Jurisdiction jurisdiction) {
    }

    private static void validateIdempotencyKey(Commands.InviteMember cmd) {
        if (cmd.idempotencyKey() == null || cmd.idempotencyKey().isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
    }

    private static Results.MembershipView toView(Membership m) {
        return new Results.MembershipView(
                m.id(),
                m.tenantId(),
                m.userId(),
                m.role(),
                m.status().name(),
                m.version(),
                m.invitedAt(),
                m.joinedAt(),
                m.suspendedAt(),
                m.revokedAt());
    }

    public static class MembershipNotFoundException extends RuntimeException {
        public MembershipNotFoundException(String message) {
            super(message);
        }
    }

    public static class InvalidMembershipStateException extends RuntimeException {
        public InvalidMembershipStateException(String message) {
            super(message);
        }
    }

    public static class InvalidInviteTokenException extends RuntimeException {
        public InvalidInviteTokenException(String message) {
            super(message);
        }
    }

    public static class CrossTenantMembershipException extends RuntimeException {
        public CrossTenantMembershipException(String message) {
            super(message);
        }
    }

    public static class OptimisticConcurrencyException extends RuntimeException {
        public OptimisticConcurrencyException(String message) {
            super(message);
        }
    }
}
