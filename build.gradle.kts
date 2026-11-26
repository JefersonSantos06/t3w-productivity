import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.Coordinates

plugins {
    id("java") // Java support
    alias(libs.plugins.intelliJPlatform) // IntelliJ Platform Gradle Plugin
    alias(libs.plugins.changelog) // Gradle Changelog Plugin
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

// Set the JVM language level used to build the project.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// Configure project's dependencies
repositories {
    mavenCentral()

    // IntelliJ Platform Gradle Plugin Repositories Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
    intellijPlatform {
        defaultRepositories()
    }
}

// Dependencies are managed with Gradle version catalog - read more: https://docs.gradle.org/current/userguide/version_catalogs.html
dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.opentest4j)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))

        // Plugin Dependencies. Uses `platformBundledPlugins` property from the gradle.properties file for bundled IntelliJ Platform plugins.
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })

        // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file for plugin from JetBrains Marketplace.
        plugins(providers.gradleProperty("platformPlugins").map { it.split(',') })

        // Module Dependencies. Uses `platformBundledModules` property from the gradle.properties file for bundled IntelliJ Platform modules.
        bundledModules(providers.gradleProperty("platformBundledModules").map { it.split(',') })

        // Só para os testes de web-types: no Ultimate é o plugin JavaScript que liga os web-types embutidos ao HTML/CSS.
        // O plugin não depende de nenhum dos dois em runtime.
        testBundledPlugin("JavaScript")
        testBundledPlugin("com.intellij.css")

        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
    }
}

// IntelliJ Community (sem os plugins JavaScript, CSS e Database): ./gradlew runIdeCommunity e ./gradlew testCommunity
// DataGrip (sem Java): ./gradlew runIdeDataGrip e ./gradlew testDataGrip. A versão 2025.2.5 existe nos dois produtos.
intellijPlatformTesting {
    runIde.register("runIdeCommunity") {
        type = IntelliJPlatformType.IntellijIdeaCommunity
        version = providers.gradleProperty("platformVersion")
    }

    testIde.register("testCommunity") {
        type = IntelliJPlatformType.IntellijIdeaCommunity
        version = providers.gradleProperty("platformVersion")

        task {
            useJUnit()
            filter {
                includeTestsMatching("*.VaadinWebTypesSmokeTest")
                includeTestsMatching("*.OptionalDependenciesSmokeTest")
            }
        }
    }

    runIde.register("runIdeDataGrip") {
        type = IntelliJPlatformType.DataGrip
        version = providers.gradleProperty("platformVersion")
    }

    testIde.register("testDataGrip") {
        type = IntelliJPlatformType.DataGrip
        version = providers.gradleProperty("platformVersion")

        task {
            useJUnit()
            filter {
                includeTestsMatching("*.OptionalDependenciesSmokeTest")
            }
        }
    }
}

// O DataGrip não traz o testFramework.jar nem o idea_rt.jar (o IntelliJ traz); usa os publicados mais próximos do
// build dele.
dependencies {
    intellijPlatform {
        testFramework(TestFrameworkType.Platform, configurationName = "intellijPlatformTestDependencies_testDataGrip")
        testPlatformDependency(Coordinates("com.jetbrains.intellij.java", "java-rt"),
            configurationName = "intellijPlatformTestDependencies_testDataGrip")
    }
}

// Configure IntelliJ Platform Gradle Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
        description = providers.gradleProperty("pluginDescription")

        // "O que há de novo": seção da versão atual no CHANGELOG.md (ou Unreleased, se ainda não existir)
        val changelog = project.changelog // variável local por causa do configuration cache
        changeNotes = providers.gradleProperty("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
        }
    }

    // ./gradlew verifyPlugin: APIs usadas contra o DataGrip. Ele ignora o que depende do Java (opcional e ausente);
    // o carregamento sem Java é coberto pelo ./gradlew testDataGrip.
    pluginVerification {
        ides {
            create(IntelliJPlatformType.DataGrip, providers.gradleProperty("platformVersion"))
        }
    }
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    groups = listOf("Novidades", "Alterações", "Correções")
    versionPrefix = ""
}

tasks {
    test {
        useJUnit()
    }

    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }
}
