package com.genealogy.platform.services.research.domain;

import java.util.Locale;

/**
 * Closed-set classification of the {@code Repository} (the
 * container that groups sources). Mirrors
 * `contracts/research/research-policy.yaml::
 * spec.repositoryKinds` (E6.1) and `requirements.md` R8.1
 * (repository).
 *
 * <ul>
 *   <li>{@link #ARCHIVE} — a national or regional archive
 *       (e.g. National Archives of Vietnam — Trung tâm Lưu
 *       trữ quốc gia).
 *   <li>{@link #LIBRARY} — a library or special collection.
 *   <li>{@link #CHURCH} — a parish, diocese or congregational
 *       register.
 *   <li>{@link #CIVIL_REGISTRY} — a civil registry office.
 *   <li>{@link #CEMETERY} — a cemetery office or memorial
 *       database.
 *   <li>{@link #FAMILY_HOLDING} — a privately-held family
 *       collection (Bible, family register, photograph
 *       album).
 *   <li>{@link #DIGITAL_PLATFORM} — a digital genealogy
 *       platform (FamilySearch, MyHeritage, Ancestry,
 *       WikiTree).
 *   <li>{@link #OTHER} — explicit escape hatch.
 * </ul>
 *
 * The wire vocabulary is enforced by the lint-research-config
 * script.
 */
public enum RepositoryKind {
    ARCHIVE,
    LIBRARY,
    CHURCH,
    CIVIL_REGISTRY,
    CEMETERY,
    FAMILY_HOLDING,
    DIGITAL_PLATFORM,
    OTHER;

    public static RepositoryKind fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("repositoryKind must not be null");
        }
        return RepositoryKind.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
