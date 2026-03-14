package zed.rainxch.details.presentation

import org.jetbrains.compose.resources.StringResource
import zed.rainxch.core.domain.model.GithubAsset
import zed.rainxch.core.domain.model.GithubRelease
import zed.rainxch.details.domain.model.ReleaseCategory
import zed.rainxch.details.presentation.model.TranslationTarget

sealed interface DetailsAction {
    data object Retry : DetailsAction

    data object InstallPrimary : DetailsAction

    data object OnDismissDowngradeWarning : DetailsAction

    data object UninstallApp : DetailsAction
    data object OnRequestUninstall : DetailsAction
    data object OnDismissUninstallConfirmation : DetailsAction
    data object OnConfirmUninstall : DetailsAction

    data class DownloadAsset(
        val downloadUrl: String,
        val assetName: String,
        val sizeBytes: Long,
    ) : DetailsAction

    data object CancelCurrentDownload : DetailsAction

    data object OpenRepoInBrowser : DetailsAction

    data object OpenAuthorInBrowser : DetailsAction

    data class OpenDeveloperProfile(
        val username: String,
    ) : DetailsAction

    data object OpenInObtainium : DetailsAction

    data object OpenInAppManager : DetailsAction

    data object InstallWithExternalApp : DetailsAction

    data object OpenWithExternalInstaller : DetailsAction

    data object DismissExternalInstallerPrompt : DetailsAction

    data object OnToggleInstallDropdown : DetailsAction

    data object OnNavigateBackClick : DetailsAction

    data object OnToggleFavorite : DetailsAction

    data object OnShareClick : DetailsAction

    data object UpdateApp : DetailsAction

    data object OpenApp : DetailsAction

    data class OnMessage(
        val messageText: StringResource,
    ) : DetailsAction

    data class SelectReleaseCategory(
        val category: ReleaseCategory,
    ) : DetailsAction

    data class SelectRelease(
        val release: GithubRelease,
    ) : DetailsAction

    data object ToggleVersionPicker : DetailsAction

    data object ToggleAboutExpanded : DetailsAction

    data object ToggleWhatsNewExpanded : DetailsAction

    data class TranslateAbout(
        val targetLanguageCode: String,
    ) : DetailsAction

    data class TranslateWhatsNew(
        val targetLanguageCode: String,
    ) : DetailsAction

    data object ToggleAboutTranslation : DetailsAction

    data object ToggleWhatsNewTranslation : DetailsAction

    data class ShowLanguagePicker(
        val target: TranslationTarget,
    ) : DetailsAction

    data object DismissLanguagePicker : DetailsAction

    // show release asset picker
    data class SelectDownloadAsset(
        val release: GithubAsset,
    ) : DetailsAction

    data object ToggleReleaseAssetsPicker : DetailsAction
}
