package xdu.undarubra.esmp.plugin.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import org.junit.jupiter.api.Test;

class ArchitectureTest {
    private static final JavaClasses CLASSES =
            new ClassFileImporter()
                    .withImportOption(new ImportOption.DoNotIncludeTests())
                    .importPackages("xdu.undarubra.esmp");

    @Test
    void coreHasNoGameplayOrPaperDependency() {
        noClasses()
                .that()
                .resideInAPackage("xdu.undarubra.esmp.core..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "xdu.undarubra.esmp.civilization..",
                        "xdu.undarubra.esmp.economy..",
                        "xdu.undarubra.esmp.resource..",
                        "org.bukkit..",
                        "io.papermc..")
                .check(CLASSES);
    }

    @Test
    void gameplayAndPublicApisDoNotExposePersistenceTypes() {
        noClasses()
                .that()
                .resideOutsideOfPackages("..persistence..", "..generated..", "..core.storage..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.jooq..", "..generated..")
                .check(CLASSES);
        noClasses()
                .that()
                .resideOutsideOfPackages("..persistence..", "..core.storage..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("xdu.undarubra.esmp.core.storage.DatabaseAccess")
                .check(CLASSES);
    }

    @Test
    void domainsDoNotReadOtherDomainsPersistence() {
        for (String domain : new String[] {"core", "civilization", "economy", "resource"}) {
            for (String other : new String[] {"core", "civilization", "economy", "resource"}) {
                if (domain.equals(other)) continue;
                noClasses()
                        .that()
                        .resideInAPackage("xdu.undarubra.esmp." + domain + "..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(
                                "xdu.undarubra.esmp." + other + ".persistence..",
                                "xdu.undarubra.esmp." + other + ".generated..")
                        .check(CLASSES);
            }
        }
        noClasses()
                .that()
                .resideInAPackage("xdu.undarubra.esmp.plugin..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "xdu.undarubra.esmp..persistence..", "xdu.undarubra.esmp..generated..")
                .check(CLASSES);
    }

    @Test
    void gameplayHasNoPaperOrJdbcStatements() {
        noClasses()
                .that()
                .resideInAnyPackage(
                        "xdu.undarubra.esmp.civilization..",
                        "xdu.undarubra.esmp.economy..",
                        "xdu.undarubra.esmp.resource..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.bukkit..", "io.papermc..")
                .check(CLASSES);
        noClasses()
                .that()
                .resideOutsideOfPackages(
                        "..persistence.legacy..", "..core.storage..", "..core.persistence.legacy..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("java.sql.Statement")
                .check(CLASSES);
        noClasses()
                .that()
                .resideOutsideOfPackages(
                        "..persistence.legacy..", "..core.storage..", "..core.persistence.legacy..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("java.sql.PreparedStatement")
                .check(CLASSES);
    }

    @Test
    void resourceAndEconomyCannotDependOnCivilization() {
        noClasses()
                .that()
                .resideInAnyPackage("xdu.undarubra.esmp.resource..", "xdu.undarubra.esmp.economy..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("xdu.undarubra.esmp.civilization..")
                .check(CLASSES);
    }
}
