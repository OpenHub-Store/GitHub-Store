package zed.rainxch.search.presentation.utils

import org.jetbrains.compose.resources.StringResource
import zed.rainxch.domain.model.ProgrammingLanguage
import zed.rainxch.domain.model.ProgrammingLanguage.*
import zed.rainxch.githubstore.core.presentation.res.*

fun ProgrammingLanguage.label(): StringResource =
    when (this) {
        All -> Res.string.language_all
        Kotlin -> Res.string.language_kotlin
        Java -> Res.string.language_java
        JavaScript -> Res.string.language_javascript
        TypeScript -> Res.string.language_typescript
        Python -> Res.string.language_python
        Swift -> Res.string.language_swift
        Rust -> Res.string.language_rust
        Go -> Res.string.language_go
        CSharp -> Res.string.language_csharp
        CPlusPlus -> Res.string.language_cpp
        C -> Res.string.language_c
        Dart -> Res.string.language_dart
        Ruby -> Res.string.language_ruby
        PHP -> Res.string.language_php
    }
