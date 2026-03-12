package zed.rainxch.githubstore.convention

import org.gradle.api.Project
import java.util.Locale

fun Project.pathToPackageName(): String {
    val relativePackageName =
        path
            .replace(":", ".")
            .replace("-", "_")
            .lowercase()

    return "zed.rainxch$relativePackageName"
}

fun Project.pathToResourcePrefix(): String =
    path
        .replace(":", "_ ")
        .lowercase()
        .drop(1) + "_"
