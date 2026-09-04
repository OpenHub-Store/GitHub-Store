package zed.rainxch.repopages.presentation.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.scaffold.KomiScaffold
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.locals.LocalStatusColors
import zed.rainxch.core.presentation.utils.formatIsoDate
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.repo_pages_security_advisories_header
import zed.rainxch.githubstore.core.presentation.res.repo_pages_security_no_advisories
import zed.rainxch.githubstore.core.presentation.res.repo_pages_security_no_policy
import zed.rainxch.githubstore.core.presentation.res.repo_pages_security_policy_header
import zed.rainxch.githubstore.core.presentation.res.repo_pages_security_title
import zed.rainxch.githubstore.core.presentation.res.repo_pages_severity_critical
import zed.rainxch.githubstore.core.presentation.res.repo_pages_severity_high
import zed.rainxch.githubstore.core.presentation.res.repo_pages_severity_low
import zed.rainxch.githubstore.core.presentation.res.repo_pages_severity_medium
import zed.rainxch.githubstore.core.presentation.res.repo_pages_severity_unknown
import zed.rainxch.repopages.domain.model.AdvisorySeverity
import zed.rainxch.repopages.domain.model.SecurityAdvisory
import zed.rainxch.repopages.domain.model.SecurityOverview
import zed.rainxch.repopages.presentation.components.RepoMarkdown
import zed.rainxch.repopages.presentation.components.RepoPagesError
import zed.rainxch.repopages.presentation.components.RepoPagesLoading
import zed.rainxch.repopages.presentation.components.RepoPagesTopBar

@Composable
fun SecurityRoot(
    onNavigateBack: () -> Unit,
    viewModel: SecurityViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SecurityScreen(
        state = state,
        onAction = { action ->
            when (action) {
                SecurityAction.OnBackClick -> onNavigateBack()
                else -> viewModel.onAction(action)
            }
        },
    )
}

@Composable
private fun SecurityScreen(
    state: SecurityUiState,
    onAction: (SecurityAction) -> Unit,
) {
    KomiScaffold(
        topBar = {
            RepoPagesTopBar(
                title = stringResource(Res.string.repo_pages_security_title),
                onBack = { onAction(SecurityAction.OnBackClick) },
            )
        },
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        when {
            state.isLoading -> RepoPagesLoading(modifier = contentModifier)
            state.errorMessage != null -> RepoPagesError(
                message = state.errorMessage,
                onRetry = { onAction(SecurityAction.OnRetry) },
                modifier = contentModifier,
            )
            state.overview != null -> SecurityContent(
                overview = state.overview,
                modifier = contentModifier,
            )
        }
    }
}

@Composable
private fun SecurityContent(
    overview: SecurityOverview,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "advisories_header") {
            SectionHeader(stringResource(Res.string.repo_pages_security_advisories_header))
        }

        if (overview.advisories.isEmpty()) {
            item(key = "advisories_empty") {
                KomiText(
                    text = stringResource(Res.string.repo_pages_security_no_advisories),
                    role = KomiTextRole.Body,
                    color = colors.onSurfaceVariant,
                )
            }
        } else {
            items(overview.advisories, key = { it.ghsaId }) { advisory ->
                AdvisoryCard(advisory)
            }
        }

        item(key = "policy_header") {
            SectionHeader(stringResource(Res.string.repo_pages_security_policy_header))
        }
        item(key = "policy_body") {
            val policy = overview.securityPolicyMarkdown
            if (policy.isNullOrBlank()) {
                KomiText(
                    text = stringResource(Res.string.repo_pages_security_no_policy),
                    role = KomiTextRole.Body,
                    color = colors.onSurfaceVariant,
                )
            } else {
                KomiSurface(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    RepoMarkdown(content = policy, modifier = Modifier.fillMaxWidth().padding(14.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    val colors = LocalPersonality.current.colors
    KomiText(
        text = text,
        role = KomiTextRole.Title,
        fontWeight = FontWeight.SemiBold,
        color = colors.onBackground,
        uppercase = false,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun AdvisoryCard(advisory: SecurityAdvisory) {
    val colors = LocalPersonality.current.colors
    KomiSurface(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KomiText(
                text = advisory.summary,
                role = KomiTextRole.Title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                uppercase = false,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SeverityBadge(advisory.severity)

                val meta = buildString {
                    advisory.cveId?.let { append(it) }
                    advisory.publishedAt?.let {
                        if (isNotEmpty()) append(" · ")
                        append(formatIsoDate(it) ?: it.take(10))
                    }
                }
                if (meta.isNotEmpty()) {
                    KomiText(
                        text = meta,
                        role = KomiTextRole.Label,
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                        uppercase = false,
                    )
                }
            }

            advisory.description?.takeIf { it.isNotBlank() }?.let { description ->
                RepoMarkdown(content = description, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SeverityBadge(severity: AdvisorySeverity) {
    val statusColors = LocalStatusColors.current
    val shape = LocalPersonality.current.shape
    val color = when (severity) {
        AdvisorySeverity.CRITICAL -> statusColors.severityCritical
        AdvisorySeverity.HIGH -> statusColors.severityHigh
        AdvisorySeverity.MEDIUM -> statusColors.severityMedium
        AdvisorySeverity.LOW -> statusColors.severityLow
        AdvisorySeverity.UNKNOWN -> statusColors.severityUnknown
    }
    val label = when (severity) {
        AdvisorySeverity.CRITICAL -> stringResource(Res.string.repo_pages_severity_critical)
        AdvisorySeverity.HIGH -> stringResource(Res.string.repo_pages_severity_high)
        AdvisorySeverity.MEDIUM -> stringResource(Res.string.repo_pages_severity_medium)
        AdvisorySeverity.LOW -> stringResource(Res.string.repo_pages_severity_low)
        AdvisorySeverity.UNKNOWN -> stringResource(Res.string.repo_pages_severity_unknown)
    }
    KomiText(
        text = label,
        role = KomiTextRole.Label,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        uppercase = false,
        modifier = Modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(shape.cornerSmall))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
