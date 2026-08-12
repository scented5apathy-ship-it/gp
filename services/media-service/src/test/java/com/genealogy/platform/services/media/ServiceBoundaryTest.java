package com.genealogy.platform.services.media;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

/**
 * Cross-service boundary guard for {@code media-service}.
 *
 * <p>This test enforces the E1.1 acceptance criterion: a service must
 * NOT import another service's {@code db/} (Flyway / jOOQ) or
 * {@code domain/} (entities, value objects) package. Violations cause
 * the build to fail.
 */
@AnalyzeClasses(
    packages = "com.genealogy.platform.services.media",
    importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeJars.class}
)
class ServiceBoundaryTest {

    @ArchTest
    static final ArchRule SERVICES_MUST_NOT_IMPORT_OTHER_SERVICES_DB =
        ArchRuleDefinition.noClasses()
            .that().resideInAPackage("com.genealogy.platform.services.media..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "com.genealogy.platform.services.tenant.db..",
                "com.genealogy.platform.services.genealogy.db..",
                "com.genealogy.platform.services.research.db..",
                "com.genealogy.platform.services.collaboration.db..",
                "com.genealogy.platform.services.search.db..",
                "com.genealogy.platform.services.importexport.db..",
                "com.genealogy.platform.services.dna.db..",
                "com.genealogy.platform.services.notification.db..",
                "com.genealogy.platform.services.reporting.db..",
                "com.genealogy.platform.services.audit.db.."
            )
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule SERVICES_MUST_NOT_IMPORT_OTHER_SERVICES_DOMAIN =
        ArchRuleDefinition.noClasses()
            .that().resideInAPackage("com.genealogy.platform.services.media..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "com.genealogy.platform.services.tenant.domain..",
                "com.genealogy.platform.services.genealogy.domain..",
                "com.genealogy.platform.services.research.domain..",
                "com.genealogy.platform.services.collaboration.domain..",
                "com.genealogy.platform.services.search.domain..",
                "com.genealogy.platform.services.importexport.domain..",
                "com.genealogy.platform.services.dna.domain..",
                "com.genealogy.platform.services.notification.domain..",
                "com.genealogy.platform.services.reporting.domain..",
                "com.genealogy.platform.services.audit.domain.."
            )
            .allowEmptyShould(true);
}
