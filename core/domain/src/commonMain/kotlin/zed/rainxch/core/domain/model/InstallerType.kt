package zed.rainxch.core.domain.model

enum class InstallerType {
    DEFAULT,
    SHIZUKU;

    companion object {
        fun fromName(name: String?): InstallerType =
            entries.find { it.name == name } ?: DEFAULT
    }
}
