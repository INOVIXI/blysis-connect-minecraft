package com.uxplima.blysis.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The fences of the common module. It is shaded into a Paper jar and a Velocity jar, so a single
 * platform type here is a class that fails to link on the other platform, at run time.
 */
@AnalyzeClasses(packages = "com.uxplima.blysis", importOptions = ImportOption.DoNotIncludeTests.class)
final class ArchitectureTest {

    @ArchTest
    static final ArchRule commonKnowsNoPlatform = noClasses()
            .that()
            .resideOutsideOfPackages("com.uxplima.blysis.paper..", "com.uxplima.blysis.velocity..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.bukkit..", "io.papermc..", "com.destroystokyo..", "com.velocitypowered..");

    // The domain is the protocol's words and the rules about them. It reads no file and opens no socket.
    @ArchTest
    static final ArchRule domainIsPure = noClasses()
            .that()
            .resideInAPackage("com.uxplima.blysis.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("java.net..", "java.nio.file..", "java.sql..", "org.spongepowered..");
}
