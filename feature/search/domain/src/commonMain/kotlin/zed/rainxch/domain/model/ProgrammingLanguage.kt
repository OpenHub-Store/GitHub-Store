package zed.rainxch.domain.model

enum class ProgrammingLanguage(
    val queryValue: String?,
) {
    All(null),
    Kotlin("kotlin"),
    Java("java"),
    JavaScript("javascript"),
    TypeScript("typescript"),
    Python("python"),
    Swift("swift"),
    Rust("rust"),
    Go("go"),
    CSharp("c#"),
    CPlusPlus("c++"),
    C("c"),
    Dart("dart"),
    Ruby("ruby"),
    PHP("php"),
    ;

    companion object {
        fun fromLanguageString(lang: String?): ProgrammingLanguage {
            if (lang == null) return All
            return entries.find {
                it.queryValue?.equals(lang, ignoreCase = true) == true
            } ?: All
        }
    }
}
