package com.genealogy.platform.services.tenant.domain.tenant;

import com.genealogy.platform.services.tenant.domain.ids.IdGenerator;
import com.genealogy.platform.services.tenant.domain.ids.TenantId;
import java.time.Instant;
import java.util.Objects;

/**
 * Tenant aggregate root. Holds the lifecycle state machine
 * ({@link TenantStatus}), the immutable identifiers and the
 * configuration that affects downstream services (locale, timezone,
 * calendar).
 *
 * <p>Instances are created via {@link #create} (factory method that
 * stamps the create event domain) or {@link #rehydrate} (for the
 * repository layer to rebuild a row from V2 columns). Mutating
 * operations ({@link #rename}, {@link #changePlan},
 * {@link #changeLocale}, {@link #changeTimezone},
 * {@link #changeCalendar}, {@link #suspend}, {@link #restore},
 * {@link #softDelete}) bump {@link #version} so optimistic
 * concurrency works; the ETag header in REST is derived from
 * {@code version}.
 *
 * <p>This class is intentionally framework-free (no Spring, no JPA,
 * no jOOQ annotations). The repository layer adapts the aggregate
 * to V2 columns and the outbox table (E3.2c).
 */
public final class Tenant {

    private final TenantId id;
    private Slug slug;
    private TenantDisplayName displayName;
    private TenantPlan plan;
    private TenantStatus status;
    private Locale locale;
    private Timezone timezone;
    private CalendarType calendar;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant suspendedAt;
    private Instant deletedAt;

    private Tenant(
            TenantId id,
            Slug slug,
            TenantDisplayName displayName,
            TenantPlan plan,
            TenantStatus status,
            Locale locale,
            Timezone timezone,
            CalendarType calendar,
            long version,
            Instant createdAt,
            Instant updatedAt,
            Instant suspendedAt,
            Instant deletedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.slug = Objects.requireNonNull(slug, "slug");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.plan = Objects.requireNonNull(plan, "plan");
        this.status = Objects.requireNonNull(status, "status");
        this.locale = locale;
        this.timezone = timezone;
        this.calendar = calendar;
        if (version < 1) {
            throw new IllegalArgumentException("version must be >= 1");
        }
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.suspendedAt = suspendedAt;
        this.deletedAt = deletedAt;
    }

    /**
     * Factory method for new tenants. Stamps the aggregate at version
     * 1 with {@code ACTIVE} status. The caller is responsible for
     * persisting the aggregate + emitting the {@code TenantCreated}
     * outbox row (E3.2c).
     *
     * @param idGenerator produces the opaque {@link TenantId}.
     * @param slug        globally-unique slug (V2 migration enforces).
     * @param displayName tenant display name (1-120 chars).
     * @param plan        initial plan (typically {@link TenantPlan#FREE}).
     * @param locale      nullable locale (null = platform default).
     * @param timezone    nullable timezone (null = {@link Timezone#PLATFORM_DEFAULT}).
     * @param calendar    nullable calendar (null = {@link CalendarType#GREGORIAN}).
     * @param clock       clock supplier for {@code createdAt} / {@code updatedAt}.
     * @return a new tenant aggregate at version 1 with status ACTIVE.
     */
    public static Tenant create(
            IdGenerator idGenerator,
            Slug slug,
            TenantDisplayName displayName,
            TenantPlan plan,
            Locale locale,
            Timezone timezone,
            CalendarType calendar,
            java.time.Clock clock) {
        Objects.requireNonNull(idGenerator, "idGenerator");
        Objects.requireNonNull(clock, "clock");
        Instant now = clock.instant();
        return new Tenant(
                new TenantId(idGenerator.nextId()),
                slug,
                displayName,
                plan,
                TenantStatus.ACTIVE,
                locale == null ? new Locale(null) : locale,
                timezone == null ? Timezone.PLATFORM_DEFAULT : timezone,
                calendar == null ? CalendarType.GREGORIAN : calendar,
                1L,
                now,
                now,
                null,
                null);
    }

    /**
     * Rehydrate a tenant from persisted V2 columns. Used by the
     * repository (E3.2c) to rebuild the aggregate from a SELECT.
     * The {@code version}, {@code status} and timestamps are
     * preserved as-is.
     */
    public static Tenant rehydrate(
            TenantId id,
            Slug slug,
            TenantDisplayName displayName,
            TenantPlan plan,
            TenantStatus status,
            Locale locale,
            Timezone timezone,
            CalendarType calendar,
            long version,
            Instant createdAt,
            Instant updatedAt,
            Instant suspendedAt,
            Instant deletedAt,
            java.time.Clock clock) {
        // Defensive consistency check that mirrors the V2 CHECK.
        if (status == TenantStatus.SUSPENDED && suspendedAt == null) {
            throw new IllegalStateException(
                    "status=SUSPENDED requires suspendedAt to be set");
        }
        if (status != TenantStatus.SUSPENDED && suspendedAt != null) {
            throw new IllegalStateException(
                    "suspendedAt set but status=" + status);
        }
        if (status == TenantStatus.DELETED && deletedAt == null) {
            throw new IllegalStateException(
                    "status=DELETED requires deletedAt to be set");
        }
        if (status != TenantStatus.DELETED && deletedAt != null) {
            throw new IllegalStateException(
                    "deletedAt set but status=" + status);
        }
        return new Tenant(
                id, slug, displayName, plan, status,
                locale, timezone, calendar,
                version, createdAt, updatedAt, suspendedAt, deletedAt);
    }

    public TenantId id() {
        return id;
    }

    public Slug slug() {
        return slug;
    }

    public TenantDisplayName displayName() {
        return displayName;
    }

    public TenantPlan plan() {
        return plan;
    }

    public TenantStatus status() {
        return status;
    }

    public Locale locale() {
        return locale;
    }

    public Timezone timezone() {
        return timezone;
    }

    public CalendarType calendar() {
        return calendar;
    }

    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant suspendedAt() {
        return suspendedAt;
    }

    public Instant deletedAt() {
        return deletedAt;
    }

    // -----------------------------------------------------------------------
    // Mutating operations. Each bumps version + updatedAt and enforces the
    // state machine. Mutations are NOT thread-safe; the application layer
    // (E3.2c) is responsible for optimistic-concurrency retries.
    // -----------------------------------------------------------------------

    public void rename(TenantDisplayName newName, java.time.Clock clock) {
        if (status == TenantStatus.DELETED) {
            throw new IllegalStateException("cannot rename a DELETED tenant");
        }
        this.displayName = Objects.requireNonNull(newName, "newName");
        bump(clock);
    }

    public void changePlan(TenantPlan newPlan, java.time.Clock clock) {
        if (status == TenantStatus.DELETED) {
            throw new IllegalStateException("cannot change plan on DELETED tenant");
        }
        this.plan = Objects.requireNonNull(newPlan, "newPlan");
        bump(clock);
    }

    public void changeLocale(Locale newLocale, java.time.Clock clock) {
        if (status == TenantStatus.DELETED) {
            throw new IllegalStateException("cannot change locale on DELETED tenant");
        }
        this.locale = newLocale == null ? new Locale(null) : newLocale;
        bump(clock);
    }

    public void changeTimezone(Timezone newTimezone, java.time.Clock clock) {
        if (status == TenantStatus.DELETED) {
            throw new IllegalStateException("cannot change timezone on DELETED tenant");
        }
        this.timezone = newTimezone == null ? Timezone.PLATFORM_DEFAULT : newTimezone;
        bump(clock);
    }

    public void changeCalendar(CalendarType newCalendar, java.time.Clock clock) {
        if (status == TenantStatus.DELETED) {
            throw new IllegalStateException("cannot change calendar on DELETED tenant");
        }
        this.calendar = newCalendar == null ? CalendarType.GREGORIAN : newCalendar;
        bump(clock);
    }

    public void suspend(java.time.Clock clock) {
        if (!status.canTransitionTo(TenantStatus.SUSPENDED)) {
            throw new IllegalStateException(
                    "cannot SUSPEND from status=" + status);
        }
        this.status = TenantStatus.SUSPENDED;
        this.suspendedAt = clock.instant();
        bump(clock);
    }

    public void restore(java.time.Clock clock) {
        if (!status.canTransitionTo(TenantStatus.ACTIVE)) {
            throw new IllegalStateException(
                    "cannot RESTORE from status=" + status);
        }
        this.status = TenantStatus.ACTIVE;
        this.suspendedAt = null;
        bump(clock);
    }

    public void softDelete(java.time.Clock clock) {
        if (status.isTerminal()) {
            throw new IllegalStateException("tenant is already DELETED");
        }
        this.status = TenantStatus.DELETED;
        this.deletedAt = clock.instant();
        bump(clock);
    }

    private void bump(java.time.Clock clock) {
        this.version += 1;
        this.updatedAt = clock.instant();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Tenant other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Tenant[id=" + id
                + ", slug=" + slug.value()
                + ", status=" + status
                + ", plan=" + plan
                + ", version=" + version + "]";
    }
}