import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmEnvironment

plugins { `java-library` }

// Dependency resolution only. The pinned direct Kotlin/D8 build remains the APK path.
repositories { google(); mavenCentral() }
val runtime by configurations.creating {
    isCanBeConsumed = false
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment.ANDROID))
    }
    resolutionStrategy.force("org.jetbrains.kotlin:kotlin-stdlib:2.3.21")
}
dependencies {
    add(runtime.name, platform("androidx.compose:compose-bom:2026.11.01"))
    add(runtime.name, "androidx.activity:activity-compose:1.13.0")
    add(runtime.name, "androidx.compose.material3:material3")
}
dependencyLocking { lockAllConfigurations() }
tasks.register("resolveRuntime") {
    doLast {
        val destination = rootDir.resolve("../../.tools/compose-build/artifacts").canonicalFile
        destination.mkdirs()
        val rows = runtime.resolvedConfiguration.resolvedArtifacts.sortedBy { it.moduleVersion.id.toString() }.map { artifact ->
            val id = artifact.moduleVersion.id
            val name = "${id.group}.${id.name}-${id.version}.${artifact.extension}"
            artifact.file.copyTo(destination.resolve(name), overwrite = true)
            listOf(id.group, id.name, id.version, name).joinToString("\t")
        }
        destination.resolve("resolved.tsv").writeText(rows.joinToString("\n", postfix="\n"))
        println("Resolved ${rows.size} pinned Compose runtime artifacts")
    }
}
