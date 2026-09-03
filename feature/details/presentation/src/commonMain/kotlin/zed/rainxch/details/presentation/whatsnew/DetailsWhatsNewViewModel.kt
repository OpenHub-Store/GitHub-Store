package zed.rainxch.details.presentation.whatsnew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.details.domain.repository.DetailsRepository
import zed.rainxch.details.domain.repository.TranslationRepository
import zed.rainxch.details.presentation.model.SupportedLanguages
import zed.rainxch.details.presentation.model.TranslationState
import zed.rainxch.details.presentation.model.WhatsNewReleaseUi
import zed.rainxch.core.presentation.utils.formatIsoDate
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.failed_to_load
import zed.rainxch.githubstore.core.presentation.res.no_release_notes
import zed.rainxch.githubstore.core.presentation.res.translation_failed

class DetailsWhatsNewViewModel(
    private val repositoryId: Long,
    private val owner: String,
    private val repo: String,
    private val sourceHost: String?,
    private val detailsRepository: DetailsRepository,
    private val translationRepository: TranslationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        DetailsWhatsNewState(deviceLanguageCode = translationRepository.getDeviceLanguageCode()),
    )
    val state = _state.asStateFlow()

    private var rawReleases: List<GithubRelease> = emptyList()
    private var noReleaseNotes: String = ""

    init {
        load()
    }

    fun retry() {
        load()
    }

    fun translate(targetLanguageCode: String) {
        val body = rawReleases.firstOrNull()?.description.orEmpty()
        if (body.isBlank()) return
        val displayName = SupportedLanguages.all.firstOrNull { it.code == targetLanguageCode }?.displayName
        _state.update {
            it.copy(
                translation = it.translation.copy(
                    isTranslating = true,
                    targetLanguageCode = targetLanguageCode,
                    targetLanguageDisplayName = displayName,
                    error = null,
                ),
            )
        }
        viewModelScope.launch {
            runCatching {
                translationRepository.translate(body, targetLanguageCode)
            }.onSuccess { result ->
                _state.update {
                    it.copy(
                        translation = it.translation.copy(
                            isTranslating = false,
                            translatedText = result.translatedText,
                            isShowingTranslation = true,
                            detectedSourceLanguage = result.detectedSourceLanguage,
                            error = null,
                        ),
                    )
                }
                rebuild()
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        translation = it.translation.copy(
                            isTranslating = false,
                            error = e.message ?: getString(Res.string.translation_failed),
                        ),
                    )
                }
            }
        }
    }

    fun toggleTranslation() {
        _state.update {
            it.copy(
                translation = it.translation.copy(
                    isShowingTranslation = !it.translation.isShowingTranslation,
                ),
            )
        }
        rebuild()
    }

    fun clearTranslation() {
        _state.update { it.copy(translation = TranslationState()) }
        rebuild()
    }

    fun showLanguagePicker() {
        _state.update {
            it.copy(
                isLanguagePickerVisible = true,
                languagePickerQuery = "",
                filteredLanguages = SupportedLanguages.all.toImmutableList(),
            )
        }
    }

    fun dismissLanguagePicker() {
        _state.update { it.copy(isLanguagePickerVisible = false) }
    }

    fun onLanguageQueryChange(query: String) {
        val filtered = if (query.isBlank()) {
            SupportedLanguages.all
        } else {
            SupportedLanguages.all.filter {
                it.displayName.contains(query, ignoreCase = true) ||
                    it.code.contains(query, ignoreCase = true)
            }
        }
        _state.update { it.copy(languagePickerQuery = query, filteredLanguages = filtered.toImmutableList()) }
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            if (noReleaseNotes.isBlank()) noReleaseNotes = getString(Res.string.no_release_notes)
            runCatching {
                val resolved = if (owner.isNotBlank() && repo.isNotBlank()) {
                    detailsRepository.getRepositoryByOwnerAndName(owner, repo, sourceHost)
                } else {
                    detailsRepository.getRepositoryById(repositoryId)
                }
                val releases = detailsRepository.getAllReleases(
                    owner = resolved.owner.login,
                    repo = resolved.name,
                    defaultBranch = resolved.defaultBranch,
                    sourceHost = sourceHost,
                )
                resolved to releases
            }.onSuccess { (resolved, releases) ->
                rawReleases = releases
                _state.update { it.copy(isLoading = false, repoName = resolved.name) }
                rebuild()
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, errorMessage = e.message ?: getString(Res.string.failed_to_load)) }
            }
        }
    }

    private fun rebuild() {
        val translation = _state.value.translation
        val releases = rawReleases.mapIndexed { index, release ->
            val isLatest = index == 0
            val translated = translation.translatedText
                ?.takeIf { isLatest && translation.isShowingTranslation }
            val body = translated
                ?: release.description?.takeIf { it.isNotBlank() }
                ?: noReleaseNotes
            WhatsNewReleaseUi(
                id = release.id,
                tagName = release.tagName,
                publishedDate = formatIsoDate(release.publishedAt)
                    ?: release.publishedAt.take(10),
                body = body,
            )
        }.toImmutableList()
        _state.update { it.copy(releases = releases) }
    }
}
