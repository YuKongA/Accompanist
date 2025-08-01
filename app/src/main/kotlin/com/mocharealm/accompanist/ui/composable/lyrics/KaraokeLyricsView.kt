package com.mocharealm.accompanist.ui.composable.lyrics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mocharealm.accompanist.lyrics.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.model.synced.SyncedLine
import com.mocharealm.accompanist.ui.theme.SFPro

@Composable
fun KaraokeLyricsView(
    listState: LazyListState,
    lyrics: SyncedLyrics,
    currentPosition: Long,
    onLineClicked: (ISyncedLine) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentTimeMs by rememberUpdatedState(currentPosition.toInt())

    val rawIndex = lyrics.getCurrentFirstHighlightLineIndexByTime(currentTimeMs)

    val focusedLineIndex = run {
        // 检查找到的行是否是伴奏
        val line = lyrics.lines.getOrNull(rawIndex) as? KaraokeLine
        if (line != null && line.isAccompaniment) {
            // 如果是伴奏，执行你的回溯策略
            var newIndex = rawIndex
            for (i in rawIndex downTo 0) {
                if (!(lyrics.lines[i] as KaraokeLine).isAccompaniment) {
                    newIndex = i
                    break
                }
            }
            newIndex
        } else {
            // 如果不是伴奏，直接使用库返回的结果
            rawIndex
        }
    }

    val isDuoView by remember {
        derivedStateOf {
            var hasStart = false
            var hasEnd = false

            if (lyrics.lines.isEmpty()) {
                return@derivedStateOf false
            }

            for (line in lyrics.lines) {
                if (line is KaraokeLine) {
                    when (line.alignment) {
                        KaraokeAlignment.Start -> hasStart = true
                        KaraokeAlignment.End -> hasEnd = true
                        else -> {}
                    }
                }
                if (hasStart && hasEnd) {
                    break
                }
            }
            hasStart && hasEnd
        }
    }

    val baseScrollOffset = with(LocalDensity.current) {
        LocalConfiguration.current.screenHeightDp.dp.toPx()
    } * 0.1f

    LaunchedEffect(focusedLineIndex) {
        if (focusedLineIndex >= 0 &&
            focusedLineIndex < lyrics.lines.size &&
            !listState.isScrollInProgress
        ) {
            val items = listState.layoutInfo.visibleItemsInfo
            val targetItem = items.firstOrNull { it.index == focusedLineIndex }
            val scrollOffset =
                (targetItem?.offset?.minus(listState.layoutInfo.viewportStartOffset + baseScrollOffset))?.toFloat()
            try {
                if (scrollOffset != null) {
                    listState.animateScrollBy(scrollOffset, tween(600))
                } else {
                    listState.animateScrollToItem(focusedLineIndex)
                }
            } catch (_: Exception) {
            }
        }
    }
    val firstLine = lyrics.lines[0]
    val showDotInIntro = remember(firstLine, currentTimeMs) {
        (firstLine.start > 5000) && (currentTimeMs in 0 until firstLine.start)
    }
    Crossfade(lyrics) { lyrics ->
        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxSize()
                .drawWithCache {
                    val graphicsLayer = obtainGraphicsLayer()
                    graphicsLayer.apply {
                        record {
                            drawContent()
                            drawRect(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.1f to Color.White,
                                    0.5f to Color.White,
                                    1f to Color.Transparent
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                    }
                    onDrawWithContent {
                        drawLayer(graphicsLayer)
                    }
                },
            contentPadding = PaddingValues(vertical = 300.dp)
        ) {
            item(key = "intro-dots") {
                // Call KaraokeBreathingDots directly if visible
                if (showDotInIntro) {
                    KaraokeBreathingDots(
                        alignment = (firstLine as? KaraokeLine)?.alignment
                            ?: KaraokeAlignment.Start,
                        startTimeMs = 0,
                        endTimeMs = firstLine.start,
                        currentTimeMs = currentTimeMs,
                    )
                }
            }

            itemsIndexed(
                items = lyrics.lines,
                key = { index, line -> "${line.start}-${line.end}-$index" }
            ) { index, line ->
                when (line) {
                    is KaraokeLine -> {
                        // Check if the line is the current focus line
                        val isCurrentFocusLine by rememberUpdatedState(line.isFocused(currentTimeMs))
                        val isLineDone by rememberUpdatedState(currentTimeMs >= line.end)

                        val lineTimeMs =
                            if (isCurrentFocusLine) currentTimeMs else if (isLineDone) line.end + 50 else 0

                        if (!line.isAccompaniment) {
                            KaraokeLineText(
                                line = line,
                                onLineClicked = onLineClicked,
                                currentTimeMs = lineTimeMs,
                                modifier = Modifier.fillMaxWidth(if (isDuoView) 0.85f else 1f)
                            )
                        } else {
                            AnimatedVisibility(
                                visible = isCurrentFocusLine,
                                enter = scaleIn(
                                    transformOrigin = TransformOrigin(
                                        if (line.alignment == KaraokeAlignment.Start) 0f else 1f,
                                        0f
                                    ),
                                    animationSpec = tween(600)
                                ) + fadeIn() + slideInVertically(
                                    animationSpec = tween(600)
                                ) + expandVertically(
                                    animationSpec = tween(600)
                                ),
                                exit = scaleOut(
                                    transformOrigin = TransformOrigin(
                                        if (line.alignment == KaraokeAlignment.Start) 0f else 1f,
                                        0f
                                    ),
                                    animationSpec = tween(600)
                                ) + fadeOut() + slideOutVertically(animationSpec = tween(600)) + shrinkVertically(
                                    animationSpec = tween(600)
                                )
                            ) {
                                KaraokeLineText(
                                    line = line,
                                    onLineClicked = onLineClicked,
                                    currentTimeMs = lineTimeMs,
                                    Modifier.fillMaxWidth(if (isDuoView) 0.85f else 1f)
                                )
                            }
                        }
                    }

                    is SyncedLine -> {
                        val style = remember {
                            TextStyle(
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = SFPro,
                                textMotion = TextMotion.Animated
                            )
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            Column(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(vertical = 8.dp, horizontal = 16.dp)
                            ) {
                                Text(text = line.content, style = style)
                                line.translation?.let {
                                    Text(it, color = Color.White.copy(0.6f))
                                }
                            }
                        }

                    }
                }
                val nextLine = lyrics.lines.getOrNull(index + 1)
                val showDotInPause = remember(line, nextLine, currentTimeMs) {
                    nextLine != null &&
                            (nextLine.start - line.end > 5000) &&
                            (currentTimeMs in line.end..nextLine.start)
                }

                if (showDotInPause) {
                    KaraokeBreathingDots(
                        alignment = (nextLine as? KaraokeLine)?.alignment ?: KaraokeAlignment.Start,
                        startTimeMs = line.end,
                        endTimeMs = nextLine!!.start,
                        currentTimeMs = currentTimeMs
                    )
                }
            }

            item("BottomSpacing") {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(LocalConfiguration.current.screenHeightDp.dp)
                )
            }
        }
    }
}