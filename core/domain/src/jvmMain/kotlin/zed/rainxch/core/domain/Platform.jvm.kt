package zed.rainxch.core.domain

import zed.rainxch.core.domain.model.Platform

actual fun getPlatform(): Platform =
    when {
        System.getProperty("os.name").lowercase().contains("win") -> Platform.WINDOWS
        System.getProperty("os.name").lowercase().contains("mac") -> Platform.MACOS
        else -> Platform.LINUX
    }
