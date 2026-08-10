package com.genealogy.platform.spring.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Closed-set audit-class taxonomy and action catalogue. Mirrors
 * {@code contracts/audit/policy.yaml::spec.auditClasses} and
 * {@code spec.actions}. Adding an entry requires bumping the
 * policy id (e.g. {@code default-audit/v1} → {@code default-audit/v2})
 * per {@code agent-execution.md} §4.4.
 *
 * <p>This is a process-local, read-only snapshot the platform
 * starter uses to validate outgoing audit events. The source of
 * truth remains the YAML contract; the registry is the in-process
 * mirror.
 */
public final class AuditClassRegistry {

    public static final String POLICY_ID = "default-audit/v1";

    public static final String CLASS_AUTH = "auth";
    public static final String CLASS_AUTHORIZATION = "authorization";
    public static final String CLASS_POLICY = "policy";
    public static final String CLASS_SUPPORT = "support";
    public static final String CLASS_DOWNLOAD = "download";
    public static final String CLASS_CONSENT = "consent";

    private static final Set<String> CLASSES = Set.of(
            CLASS_AUTH,
            CLASS_AUTHORIZATION,
            CLASS_POLICY,
            CLASS_SUPPORT,
            CLASS_DOWNLOAD,
            CLASS_CONSENT);

    private static final Map<String, String> ACTION_TO_CLASS;
    private static final Map<String, Integer> MIN_RETENTION_DAYS;

    static {
        Map<String, String> actions = new LinkedHashMap<>();
        // auth
        actions.put("auth.login.succeeded", CLASS_AUTH);
        actions.put("auth.login.failed", CLASS_AUTH);
        actions.put("auth.logout", CLASS_AUTH);
        actions.put("auth.session.revoked", CLASS_AUTH);
        actions.put("auth.mfa.challenged", CLASS_AUTH);
        actions.put("auth.mfa.succeeded", CLASS_AUTH);
        actions.put("auth.mfa.failed", CLASS_AUTH);
        // authorization
        actions.put("tenant.created", CLASS_AUTHORIZATION);
        actions.put("tenant.updated", CLASS_AUTHORIZATION);
        actions.put("tenant.plan_changed", CLASS_AUTHORIZATION);
        actions.put("tenant.suspended", CLASS_AUTHORIZATION);
        actions.put("tenant.restored", CLASS_AUTHORIZATION);
        actions.put("tenant.soft_deleted", CLASS_AUTHORIZATION);
        actions.put("membership.invited", CLASS_AUTHORIZATION);
        actions.put("membership.activated", CLASS_AUTHORIZATION);
        actions.put("membership.role_changed", CLASS_AUTHORIZATION);
        actions.put("membership.revoked", CLASS_AUTHORIZATION);
        actions.put("openfga.tuple_written", CLASS_AUTHORIZATION);
        actions.put("openfga.tuple_revoked", CLASS_AUTHORIZATION);
        // policy
        actions.put("abac.policy_reloaded", CLASS_POLICY);
        actions.put("abac.reason_registered", CLASS_POLICY);
        actions.put("abac.cache_invalidated", CLASS_POLICY);
        actions.put("trusted_context.policy_reloaded", CLASS_POLICY);
        actions.put("audit.policy_reloaded", CLASS_POLICY);
        // support
        actions.put("support.session.started", CLASS_SUPPORT);
        actions.put("support.session.scope_granted", CLASS_SUPPORT);
        actions.put("support.session.ended", CLASS_SUPPORT);
        actions.put("support.read.executed", CLASS_SUPPORT);
        actions.put("support.export.requested", CLASS_SUPPORT);
        // download
        actions.put("download.signed_url_issued", CLASS_DOWNLOAD);
        actions.put("download.asset.exported", CLASS_DOWNLOAD);
        actions.put("download.export.bundle_signed", CLASS_DOWNLOAD);
        actions.put("download.gedcom.exported", CLASS_DOWNLOAD);
        actions.put("download.report.exported", CLASS_DOWNLOAD);
        // consent
        actions.put("consent.granted", CLASS_CONSENT);
        actions.put("consent.revoked", CLASS_CONSENT);
        actions.put("consent.expired", CLASS_CONSENT);
        actions.put("consent.access_logged", CLASS_CONSENT);
        actions.put("consent.receipt_signed", CLASS_CONSENT);
        ACTION_TO_CLASS = Map.copyOf(actions);

        Map<String, Integer> retention = new LinkedHashMap<>();
        retention.put(CLASS_AUTH, 365);
        retention.put(CLASS_AUTHORIZATION, 365);
        retention.put(CLASS_POLICY, 730);
        retention.put(CLASS_SUPPORT, 730);
        retention.put(CLASS_DOWNLOAD, 730);
        retention.put(CLASS_CONSENT, 1825);
        MIN_RETENTION_DAYS = Map.copyOf(retention);
    }

    private AuditClassRegistry() {
    }

    public static Set<String> classes() {
        return CLASSES;
    }

    public static Map<String, String> actions() {
        return ACTION_TO_CLASS;
    }

    public static int minRetentionDays(String auditClass) {
        return MIN_RETENTION_DAYS.getOrDefault(auditClass, 365);
    }

    public static boolean isKnownClass(String auditClass) {
        return auditClass != null && CLASSES.contains(auditClass);
    }

    public static boolean isKnownAction(String action) {
        return action != null && ACTION_TO_CLASS.containsKey(action);
    }

    public static String classFor(String action) {
        Objects.requireNonNull(action, "action");
        return ACTION_TO_CLASS.get(action);
    }
}
