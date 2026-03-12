package zed.rainxch.githubstore.app.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fletchmckee.liquid.liquid
import io.github.fletchmckee.liquid.rememberLiquidState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.getPlatform
import zed.rainxch.core.domain.model.Platform
import zed.rainxch.core.presentation.locals.LocalBottomNavigationLiquid
import zed.rainxch.core.presentation.theme.GithubStoreTheme
import zed.rainxch.core.presentation.utils.isLiquidFrostAvailable

@Composable
fun BottomNavigation(
    currentScreen: GithubStoreGraph,
    onNavigate: (GithubStoreGraph) -> Unit,
    isUpdateAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    val liquidState = LocalBottomNavigationLiquid.current

    if (currentScreen !in BottomNavigationUtils.allowedScreens()) return

    val visibleItems =
        remember {
            BottomNavigationUtils.items().filterNot {
                getPlatform() != Platform.ANDROID &&
                    it.screen == GithubStoreGraph.AppsScreen
            }
        }

    val selectedIndex = visibleItems.indexOfFirst { it.screen == currentScreen }

    val itemPositions = remember { mutableMapOf<Int, Pair<Float, Float>>() }

    var selectedItemPos by remember { mutableStateOf<Pair<Float, Float>?>(null) }

    val rowHorizontalPaddingDp = 6.dp
    val density = LocalDensity.current
    val rowHorizontalPaddingPx = with(density) { rowHorizontalPaddingDp.toPx() }

    val indicatorHorizontalInsetPx = with(density) { 4.dp.toPx() }

    val indicatorX = remember { Animatable(0f) }
    val indicatorWidth = remember { Animatable(0f) }

    LaunchedEffect(selectedIndex, selectedItemPos) {
        val raw = selectedItemPos ?: itemPositions[selectedIndex] ?: return@LaunchedEffect
        val targetX = raw.first + rowHorizontalPaddingPx - indicatorHorizontalInsetPx
        val targetW = raw.second + indicatorHorizontalInsetPx * 2f
        launch {
            indicatorX.animateTo(
                targetValue = targetX,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow,
                    ),
            )
        }
        launch {
            indicatorWidth.animateTo(
                targetValue = targetW,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
            )
        }
    }

    val isDarkTheme =
        !MaterialTheme.colorScheme.background
            .luminance()
            .let { it > 0.5f }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .clip(CircleShape)
                    .background(
                        if (isLiquidFrostAvailable()) {
                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(
                                alpha = if (isDarkTheme) .25f else .15f,
                            )
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                    ).then(
                        if (isLiquidFrostAvailable()) {
                            Modifier.liquid(liquidState) {
                                this.shape = CircleShape
                                this.frost = if (isDarkTheme) 12.dp else 10.dp
                                this.curve = if (isDarkTheme) .35f else .45f
                                this.refraction = if (isDarkTheme) .08f else .12f
                                this.dispersion = if (isDarkTheme) .18f else .25f
                                this.saturation = if (isDarkTheme) .40f else .55f
                                this.contrast = if (isDarkTheme) 1.8f else 1.6f
                            }
                        } else {
                            Modifier
                        },
                    ).pointerInput(Unit) { },
        ) {
            val glassHighColor =
                if (isDarkTheme) {
                    Color.White.copy(alpha = .12f)
                } else {
                    Color.White.copy(alpha = .30f)
                }
            val glassLowColor =
                if (isDarkTheme) {
                    Color.White.copy(alpha = .04f)
                } else {
                    Color.White.copy(alpha = .10f)
                }
            val specularColor =
                if (isDarkTheme) {
                    Color.White.copy(alpha = .18f)
                } else {
                    Color.White.copy(alpha = .45f)
                }
            val innerGlowColor =
                if (isDarkTheme) {
                    Color.White.copy(alpha = .03f)
                } else {
                    Color.White.copy(alpha = .08f)
                }
            val borderColor =
                if (isDarkTheme) {
                    Color.White.copy(alpha = .08f)
                } else {
                    Color.Transparent
                }

            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .drawBehind {
                            if (indicatorWidth.value > 0f) {
                                if (isDarkTheme) {
                                    drawRoundRect(
                                        color = borderColor,
                                        topLeft =
                                            Offset(
                                                indicatorX.value - .5.dp.toPx(),
                                                1.5.dp.toPx(),
                                            ),
                                        size =
                                            Size(
                                                indicatorWidth.value + 1.dp.toPx(),
                                                size.height - 3.dp.toPx(),
                                            ),
                                        cornerRadius = CornerRadius(size.height / 2f),
                                        style = Stroke(width = 1.dp.toPx()),
                                    )
                                }

                                drawRoundRect(
                                    brush =
                                        Brush.verticalGradient(
                                            colors = listOf(glassHighColor, glassLowColor),
                                        ),
                                    topLeft = Offset(indicatorX.value, 2.dp.toPx()),
                                    size = Size(indicatorWidth.value, size.height - 4.dp.toPx()),
                                    cornerRadius = CornerRadius(size.height / 2f),
                                )

                                drawRoundRect(
                                    brush =
                                        Brush.horizontalGradient(
                                            colors =
                                                listOf(
                                                    Color.Transparent,
                                                    specularColor,
                                                    Color.Transparent,
                                                ),
                                            startX = indicatorX.value + indicatorWidth.value * .15f,
                                            endX = indicatorX.value + indicatorWidth.value * .85f,
                                        ),
                                    topLeft =
                                        Offset(
                                            indicatorX.value + indicatorWidth.value * .15f,
                                            3.dp.toPx(),
                                        ),
                                    size = Size(indicatorWidth.value * .7f, 1.5.dp.toPx()),
                                    cornerRadius = CornerRadius(1.dp.toPx()),
                                )

                                drawRoundRect(
                                    brush =
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, innerGlowColor),
                                        ),
                                    topLeft =
                                        Offset(
                                            indicatorX.value + 4.dp.toPx(),
                                            size.height - 8.dp.toPx(),
                                        ),
                                    size = Size(indicatorWidth.value - 8.dp.toPx(), 4.dp.toPx()),
                                    cornerRadius = CornerRadius(2.dp.toPx()),
                                )
                            }
                        },
            )

            Row(
                modifier = Modifier.padding(horizontal = rowHorizontalPaddingDp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                visibleItems.forEachIndexed { index, item ->
                    LiquidGlassTabItem(
                        item = item,
                        hasBadge = item.screen == GithubStoreGraph.AppsScreen && isUpdateAvailable,
                        isSelected = item.screen == currentScreen,
                        onSelect = { onNavigate(item.screen) },
                        onPositioned = { x, width ->
                            itemPositions[index] = x to width
                            if (index == selectedIndex) {
                                selectedItemPos = x to width
                            }
                            if (index == selectedIndex && indicatorWidth.value == 0f) {
                                val snapX = x + rowHorizontalPaddingPx - indicatorHorizontalInsetPx
                                val snapW = width + indicatorHorizontalInsetPx * 2f
                                indicatorX.snapTo(snapX)
                                indicatorWidth.snapTo(snapW)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LiquidGlassTabItem(
    item: BottomNavigationItem,
    isSelected: Boolean,
    onSelect: () -> Unit,
    hasBadge: Boolean = false,
    onPositioned: suspend (x: Float, width: Float) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "pressScale",
    )

    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        label = "iconScale",
    )

    val iconOffsetY by animateDpAsState(
        targetValue = if (isSelected) (-1).dp else 1.dp,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        label = "iconOffsetY",
    )

    val iconTint =
        if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = .7f)
        }

    val labelAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec =
            tween(
                durationMillis = if (isSelected) 250 else 150,
                easing = FastOutSlowInEasing,
            ),
        label = "labelAlpha",
    )

    val labelScale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.6f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        label = "labelScale",
    )

    val horizontalPadding by animateDpAsState(
        targetValue = if (isSelected) 20.dp else 14.dp,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        label = "hPadding",
    )

    Box(
        modifier =
            Modifier
                .clip(CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                ) { onSelect() }
                .onGloballyPositioned { coordinates ->
                    val x = coordinates.positionInParent().x
                    val width = coordinates.size.width.toFloat()
                    scope.launch { onPositioned(x, width) }
                }.graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }.padding(horizontal = horizontalPadding, vertical = 6.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Icon(
                imageVector = if (isSelected) item.iconFilled else item.iconOutlined,
                contentDescription = stringResource(item.titleRes),
                modifier =
                    Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                            translationY = with(density) { iconOffsetY.toPx() }
                        },
                tint = iconTint,
            )

            Box(
                modifier =
                    Modifier
                        .height(if (isSelected) 16.dp else 0.dp)
                        .graphicsLayer {
                            alpha = labelAlpha
                            scaleX = labelScale
                            scaleY = labelScale
                        },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(item.titleRes),
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            lineHeight = 12.sp,
                        ),
                    color =
                        if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = .7f)
                        },
                    maxLines = 1,
                )
            }
        }

        if (hasBadge) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
                    .align(Alignment.TopEnd),
            )
        }
    }
}

@Preview
@Composable
fun BottomNavigationPreview() {
    GithubStoreTheme {
        CompositionLocalProvider(
            LocalBottomNavigationLiquid provides rememberLiquidState(),
        ) {
            BottomNavigation(
                currentScreen = GithubStoreGraph.HomeScreen,
                onNavigate = {
                },
                isUpdateAvailable = true,
            )
        }
    }
}
