package zed.rainxch.core.domain.model

enum class AppTheme {
    DYNAMIC,
    OCEAN,
    PURPLE,
    FOREST,
    SLATE,
    AMBER,
    ;

    companion object {
        fun fromName(name: String?): AppTheme = entries.find { it.name == name } ?: OCEAN
    }
}
