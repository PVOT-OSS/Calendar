import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    includeBuild("PvotLib/build-logic")
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Etar-Calendar"

includeBuild("PvotLib") {
    dependencySubstitution {
        substitute(module("com.prauga.pvot:design-system")).using(project(":design-system"))
    }
}

include(":app")
