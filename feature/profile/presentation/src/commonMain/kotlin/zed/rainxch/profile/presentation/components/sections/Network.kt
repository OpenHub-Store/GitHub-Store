package zed.rainxch.profile.presentation.components.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.githubstore.core.presentation.res.*
import zed.rainxch.profile.presentation.ProfileAction
import zed.rainxch.profile.presentation.ProfileState
import zed.rainxch.profile.presentation.components.SectionHeader
import zed.rainxch.profile.presentation.model.ProxyType

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun LazyListScope.networkSection(
    state: ProfileState,
    onAction: (ProfileAction) -> Unit,
) {
    item {
        SectionHeader(
            text = stringResource(Res.string.section_network),
        )

        Spacer(Modifier.height(8.dp))

        ProxyTypeCard(
            selectedType = state.proxyType,
            onTypeSelected = { type ->
                onAction(ProfileAction.OnProxyTypeSelected(type))
            },
        )

        AnimatedVisibility(
            visible = state.proxyType == ProxyType.NONE || state.proxyType == ProxyType.SYSTEM,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Text(
                text =
                    when (state.proxyType) {
                        ProxyType.SYSTEM -> stringResource(Res.string.proxy_system_description)
                        else -> stringResource(Res.string.proxy_none_description)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 12.dp),
            )
        }

        AnimatedVisibility(
            visible = state.proxyType == ProxyType.HTTP || state.proxyType == ProxyType.SOCKS,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                Spacer(Modifier.height(16.dp))

                ProxyDetailsCard(
                    state = state,
                    onAction = onAction,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProxyTypeCard(
    selectedType: ProxyType,
    onTypeSelected: (ProxyType) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        shape = RoundedCornerShape(32.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = stringResource(Res.string.proxy_type),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(8.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(ProxyType.entries) { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { onTypeSelected(type) },
                        label = {
                            Text(
                                text =
                                    when (type) {
                                        ProxyType.NONE -> stringResource(Res.string.proxy_none)
                                        ProxyType.SYSTEM -> stringResource(Res.string.proxy_system)
                                        ProxyType.HTTP -> stringResource(Res.string.proxy_http)
                                        ProxyType.SOCKS -> stringResource(Res.string.proxy_socks)
                                    },
                                fontWeight = if (selectedType == type) FontWeight.Bold else FontWeight.Normal,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProxyDetailsCard(
    state: ProfileState,
    onAction: (ProfileAction) -> Unit,
) {
    val portValue = state.proxyPort
    val isPortInvalid =
        portValue.isNotEmpty() &&
            (portValue.toIntOrNull()?.let { it !in 1..65535 } ?: true)
    val isFormValid =
        state.proxyHost.isNotBlank() &&
            portValue.isNotEmpty() &&
            portValue.toIntOrNull()?.let { it in 1..65535 } == true

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        shape = RoundedCornerShape(32.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = state.proxyHost,
                    onValueChange = { onAction(ProfileAction.OnProxyHostChanged(it)) },
                    label = { Text(stringResource(Res.string.proxy_host)) },
                    placeholder = { Text("127.0.0.1") },
                    singleLine = true,
                    modifier = Modifier.weight(2f),
                    shape = RoundedCornerShape(12.dp),
                )

                OutlinedTextField(
                    value = state.proxyPort,
                    onValueChange = { onAction(ProfileAction.OnProxyPortChanged(it)) },
                    label = { Text(stringResource(Res.string.proxy_port)) },
                    placeholder = { Text("1080") },
                    singleLine = true,
                    isError = isPortInvalid,
                    supportingText =
                        if (isPortInvalid) {
                            { Text(stringResource(Res.string.proxy_port_error)) }
                        } else {
                            null
                        },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                )
            }

            // Username
            OutlinedTextField(
                value = state.proxyUsername,
                onValueChange = { onAction(ProfileAction.OnProxyUsernameChanged(it)) },
                label = { Text(stringResource(Res.string.proxy_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            // Password with visibility toggle
            OutlinedTextField(
                value = state.proxyPassword,
                onValueChange = { onAction(ProfileAction.OnProxyPasswordChanged(it)) },
                label = { Text(stringResource(Res.string.proxy_password)) },
                singleLine = true,
                visualTransformation =
                    if (state.isProxyPasswordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                trailingIcon = {
                    IconButton(
                        onClick = { onAction(ProfileAction.OnProxyPasswordVisibilityToggle) },
                    ) {
                        Icon(
                            imageVector =
                                if (state.isProxyPasswordVisible) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                            contentDescription =
                                if (state.isProxyPasswordVisible) {
                                    stringResource(Res.string.proxy_hide_password)
                                } else {
                                    stringResource(Res.string.proxy_show_password)
                                },
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            // Save button
            FilledTonalButton(
                onClick = { onAction(ProfileAction.OnProxySave) },
                enabled = isFormValid,
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(stringResource(Res.string.proxy_save))
            }
        }
    }
}
