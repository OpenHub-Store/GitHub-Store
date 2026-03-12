package zed.rainxch.core.domain.model

enum class FontTheme(
    val displayName: String,
) {
    SYSTEM("System"),
    CUSTOM("JetBrains Mono + Inter"),
    ;

    companion object {
        fun fromName(name: String?): FontTheme = entries.find { it.name == name } ?: CUSTOM
    }
}
