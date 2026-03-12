package zed.rainxch.home.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.fletchmckee.liquid.liquid
import kotlinx.coroutines.launch
import zed.rainxch.core.presentation.utils.isLiquidFrostAvailable
import zed.rainxch.home.domain.model.HomeCategory
import zed.rainxch.home.presentation.locals.LocalHomeTopBarLiquid
import zed.rainxch.home.presentation.utils.displayText

@Composable
fun LiquidGlassCategoryChips(
    categories: List<HomeCategory>,
    selectedCategory: HomeCategory,
    onCategorySelected: (HomeCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val liquidState = LocalHomeTopBarLiquid.current
    val density = LocalDensity.current

    val isDarkTheme =
        !MaterialTheme.colorScheme.background
            .luminance()
            .let { it > 0.5f }

    val itemPositions = remember { mutableMapOf<Int, Pair<Float, Float>>() }
    var selectedItemPos by remember { mutableStateOf<Pair<Float, Float>?>(null) }

    val selectedIndex = categories.indexOf(selectedCategory)

    val rowPaddingDp = 6.dp
    val rowPaddingPx = with(density) { rowPaddingDp.toPx() }
    val insetPx = with(density) { 2.dp.toPx() }

    val indicatorX = remember { Animatable(0f) }
    val indicatorWidth = remember { Animatable(0f) }

    LaunchedEffect(selectedIndex, selectedItemPos) {
        val raw = selectedItemPos ?: itemPositions[selectedIndex] ?: return@LaunchedEffect
        val targetX = raw.first + rowPaddingPx - insetPx
        val targetW = raw.second + insetPx * 2f

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

    val glassHighColor =
        if (isDarkTheme) Color.White.copy(alpha = .14f) else Color.White.copy(alpha = .50f)
    val glassLowColor =
        if (isDarkTheme) Color.White.copy(alpha = .05f) else Color.White.copy(alpha = .18f)
    val specularColor =
        if (isDarkTheme) Color.White.copy(alpha = .20f) else Color.White.copy(alpha = .55f)
    val innerGlowColor =
        if (isDarkTheme) Color.White.copy(alpha = .04f) else Color.White.copy(alpha = .10f)
    val borderColor = if (isDarkTheme) Color.White.copy(alpha = .10f) else Color.Transparent

    val containerShape = RoundedCornerShape(20.dp)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(containerShape)
                .background(
                    if (isDarkTheme) {
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = .30f)
                    } else {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f)
                    },
                ).then(
                    if (isLiquidFrostAvailable()) {
                        Modifier.liquid(liquidState) {
                            this.shape = containerShape
                            this.frost = if (isDarkTheme) 14.dp else 12.dp
                            this.curve = if (isDarkTheme) .30f else .40f
                            this.refraction = if (isDarkTheme) .06f else .10f
                            this.dispersion = if (isDarkTheme) .15f else .22f
                            this.saturation = if (isDarkTheme) .35f else .50f
                            this.contrast = if (isDarkTheme) 1.7f else 1.5f
                        }
                    } else {
                        Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    },
                ),
    ) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        if (indicatorWidth.value > 0f) {
                            val pillTop = 5.dp.toPx()
                            val pillHeight = size.height - 10.dp.toPx()
                            val pillCorner = 14.dp.toPx()
                            val pillRadius = CornerRadius(pillCorner)

                            if (isDarkTheme) {
                                drawRoundRect(
                                    color = borderColor,
                                    topLeft =
                                        Offset(
                                            indicatorX.value - .5.dp.toPx(),
                                            pillTop - .5.dp.toPx(),
                                        ),
                                    size =
                                        Size(
                                            indicatorWidth.value + 1.dp.toPx(),
                                            pillHeight + 1.dp.toPx(),
                                        ),
                                    cornerRadius = pillRadius,
                                    style = Stroke(width = 1.dp.toPx()),
                                )
                            }

                            drawRoundRect(
                                brush =
                                    Brush.verticalGradient(
                                        colors = listOf(glassHighColor, glassLowColor),
                                        startY = pillTop,
                                        endY = pillTop + pillHeight,
                                    ),
                                topLeft = Offset(indicatorX.value, pillTop),
                                size = Size(indicatorWidth.value, pillHeight),
                                cornerRadius = pillRadius,
                            )

                            val specLeft = indicatorX.value + indicatorWidth.value * .12f
                            val specWidth = indicatorWidth.value * .76f
                            drawRoundRect(
                                brush =
                                    Brush.horizontalGradient(
                                        colors =
                                            listOf(
                                                Color.Transparent,
                                                specularColor,
                                                specularColor.copy(alpha = specularColor.alpha * .6f),
                                                Color.Transparent,
                                            ),
                                        startX = specLeft,
                                        endX = specLeft + specWidth,
                                    ),
                                topLeft = Offset(specLeft, pillTop + 1.dp.toPx()),
                                size = Size(specWidth, 1.5.dp.toPx()),
                                cornerRadius = CornerRadius(1.dp.toPx()),
                            )

                            drawRoundRect(
                                brush =
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, innerGlowColor),
                                        startY = pillTop + pillHeight - 6.dp.toPx(),
                                        endY = pillTop + pillHeight,
                                    ),
                                topLeft =
                                    Offset(
                                        indicatorX.value + 6.dp.toPx(),
                                        pillTop + pillHeight - 5.dp.toPx(),
                                    ),
                                size = Size(indicatorWidth.value - 12.dp.toPx(), 4.dp.toPx()),
                                cornerRadius = CornerRadius(2.dp.toPx()),
                            )

                            val edgeAlpha = if (isDarkTheme) .06f else .12f
                            drawRoundRect(
                                brush =
                                    Brush.horizontalGradient(
                                        colors =
                                            listOf(
                                                Color.White.copy(alpha = edgeAlpha),
                                                Color.Transparent,
                                            ),
                                        startX = indicatorX.value,
                                        endX = indicatorX.value + 4.dp.toPx(),
                                    ),
                                topLeft = Offset(indicatorX.value, pillTop + 4.dp.toPx()),
                                size = Size(3.dp.toPx(), pillHeight - 8.dp.toPx()),
                                cornerRadius = CornerRadius(1.5.dp.toPx()),
                            )
                            drawRoundRect(
                                brush =
                                    Brush.horizontalGradient(
                                        colors =
                                            listOf(
                                                Color.Transparent,
                                                Color.White.copy(alpha = edgeAlpha),
                                            ),
                                        startX = indicatorX.value + indicatorWidth.value - 4.dp.toPx(),
                                        endX = indicatorX.value + indicatorWidth.value,
                                    ),
                                topLeft =
                                    Offset(
                                        indicatorX.value + indicatorWidth.value - 3.dp.toPx(),
                                        pillTop + 4.dp.toPx(),
                                    ),
                                size = Size(3.dp.toPx(), pillHeight - 8.dp.toPx()),
                                cornerRadius = CornerRadius(1.5.dp.toPx()),
                            )
                        }
                    },
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rowPaddingDp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            categories.forEachIndexed { index, category ->
                LiquidGlassCategoryChip(
                    category = category,
                    isSelected = category == selectedCategory,
                    onSelect = { onCategorySelected(category) },
                    modifier = Modifier.weight(1f),
                    onPositioned = { x, width ->
                        itemPositions[index] = x to width
                        if (index == selectedIndex) {
                            selectedItemPos = x to width
                        }
                        if (index == selectedIndex && indicatorWidth.value == 0f) {
                            indicatorX.snapTo(x + rowPaddingPx - insetPx)
                            indicatorWidth.snapTo(width + insetPx * 2f)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun LiquidGlassCategoryChip(
    category: HomeCategory,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    onPositioned: suspend (x: Float, width: Float) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "chipPressScale",
    )

    val selectedAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(200),
        label = "selectedAlpha",
    )

    val textColor by animateColorAsState(
        targetValue =
            if (isSelected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = .65f)
            },
        animationSpec = tween(250),
        label = "chipTextColor",
    )

    Box(
        modifier =
            modifier
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
                }.padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = category.displayText(),
                style =
                    MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = 1f - selectedAlpha },
            )
            Text(
                text = category.displayText(),
                style =
                    MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = selectedAlpha },
            )
        }
    }
}
