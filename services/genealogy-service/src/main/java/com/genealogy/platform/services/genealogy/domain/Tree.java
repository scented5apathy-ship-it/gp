package com.genealogy.platform.services.genealogy.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Tree aggregate root. Owns locale / timezone / default calendar /
 * naming convention / branding keys / slug / visibility /
 * collaboration mode / lifecycle state.
 *
 * <p>Mirrors {@code requirements.md} R3 (tree metadata + visibility)
 * and {@code design.md} §5.2 (the conceptual genealogy model). Per
 * {@code contracts/genealogy/tree-policy.yaml::spec.slugPattern} the
 * slug is lower-case letters + digits + single hyphens, 3 to 40
 * characters.
 */
public record Tree(
        String treeId,
        String tenantId,
        String slug,
        String displayName,
        Visibility visibility,
        CollaborationMode collaborationMode,
        LifecycleState lifecycleState,
        String defaultLocale,
        String defaultTimezone,
        String defaultCalendar,
        Map<String, String> branding,
        String ownerId,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /** Slug pattern from the contract. Lower-case, 3-40 chars, no leading/trailing hyphen. */
    public static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{1,38}[a-z0-9])?$");

    public Tree {
        Objects.requireNonNull(treeId, "treeId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(collaborationMode, "collaborationMode");
        Objects.requireNonNull(lifecycleState, "lifecycleState");
        Objects.requireNonNull(defaultLocale, "defaultLocale");
        Objects.requireNonNull(defaultTimezone, "defaultTimezone");
        Objects.requireNonNull(defaultCalendar, "defaultCalendar");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        branding = branding == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(branding));
        if (!SLUG_PATTERN.matcher(slug).matches()) {
            throw new IllegalArgumentException("slug does not match pattern: " + slug);
        }
    }

    public Tree withVisibility(Visibility next, Instant at) {
        return new Tree(
                treeId,
                tenantId,
                slug,
                displayName,
                next,
                collaborationMode,
                lifecycleState,
                defaultLocale,
                defaultTimezone,
                defaultCalendar,
                branding,
                ownerId,
                version + 1,
                createdAt,
                at);
    }

    public Tree withCollaborationMode(CollaborationMode next, Instant at) {
        return new Tree(
                treeId,
                tenantId,
                slug,
                displayName,
                visibility,
                next,
                lifecycleState,
                defaultLocale,
                defaultTimezone,
                defaultCalendar,
                branding,
                ownerId,
                version + 1,
                createdAt,
                at);
    }

    public Tree withMetadata(String nextDisplayName, String nextLocale, String nextTimezone,
                             String nextCalendar, Map<String, String> nextBranding, Instant at) {
        return new Tree(
                treeId,
                tenantId,
                slug,
                nextDisplayName,
                visibility,
                collaborationMode,
                lifecycleState,
                nextLocale,
                nextTimezone,
                nextCalendar,
                nextBranding,
                ownerId,
                version + 1,
                createdAt,
                at);
    }

    public Tree archived(Instant at) {
        return new Tree(
                treeId,
                tenantId,
                slug,
                displayName,
                visibility,
                collaborationMode,
                LifecycleState.ARCHIVED,
                defaultLocale,
                defaultTimezone,
                defaultCalendar,
                branding,
                ownerId,
                version + 1,
                createdAt,
                at);
    }

    public Tree restored(Instant at) {
        return new Tree(
                treeId,
                tenantId,
                slug,
                displayName,
                visibility,
                collaborationMode,
                LifecycleState.ACTIVE,
                defaultLocale,
                defaultTimezone,
                defaultCalendar,
                branding,
                ownerId,
                version + 1,
                createdAt,
                at);
    }

    public Tree transferredTo(String newTenantId, Instant at) {
        if (newTenantId == null || newTenantId.isBlank()) {
            throw new IllegalArgumentException("newTenantId is required");
        }
        return new Tree(
                treeId,
                newTenantId,
                slug,
                displayName,
                visibility,
                collaborationMode,
                lifecycleState,
                defaultLocale,
                defaultTimezone,
                defaultCalendar,
                branding,
                ownerId,
                version + 1,
                createdAt,
                at);
    }

    public Tree deleted(Instant at) {
        return new Tree(
                treeId,
                tenantId,
                slug,
                displayName,
                visibility,
                collaborationMode,
                LifecycleState.DELETED,
                defaultLocale,
                defaultTimezone,
                defaultCalendar,
                branding,
                ownerId,
                version + 1,
                createdAt,
                at);
    }

    public static String normaliseSlug(String slug) {
        if (slug == null) {
            throw new IllegalArgumentException("slug is required");
        }
        return slug.trim().toLowerCase(Locale.ROOT);
    }
}
