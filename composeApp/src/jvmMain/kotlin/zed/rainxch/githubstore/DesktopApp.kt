package zed.rainxch.githubstore

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import githubstore.composeapp.generated.resources.Res
import githubstore.composeapp.generated.resources.app_icon
import org.jetbrains.compose.resources.painterResource
import zed.rainxch.githubstore.app.di.initKoin
import java.awt.Desktop
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val deepLinkArg = args.firstOrNull()

    if (deepLinkArg != null && DesktopDeepLink.tryForwardToRunningInstance(deepLinkArg)) {
        exitProcess(0)
    }

    DesktopDeepLink.registerUriSchemeIfNeeded()
    initKoin()

    application {
        var deepLinkUri by mutableStateOf(deepLinkArg)

        LaunchedEffect(Unit) {
            DesktopDeepLink.startInstanceListener { uri ->
                deepLinkUri = uri
            }
        }

        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().let { desktop ->
                if (desktop.isSupported(Desktop.Action.APP_OPEN_URI)) {
                    desktop.setOpenURIHandler { event ->
                        deepLinkUri = event.uri.toString()
                    }
                }
            }
        }

        Window(
            onCloseRequest = ::exitApplication,
            title = "GitHub Store",
            icon = painterResource(Res.drawable.app_icon),
        ) {
            App(deepLinkUri = deepLinkUri)
        }
    }
}
