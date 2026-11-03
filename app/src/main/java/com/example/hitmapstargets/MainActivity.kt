package com.example.hitmapstargets

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Pale = Color(0xFFF8F7F4)
private val Scarlet = Color(0xFFFF5855)
private val Green = Color(0xFFC7F7B0)
private val Muted = Color(0xFFA1A3A7)
private val Gold = Color(0xFFF9CE91)
private val NearBlack = Color(0xFF08090B)

class MainActivity : ComponentActivity() {
    private var targetFromNotification by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetFromNotification = intent.getLongExtra("target_id", -1).takeIf { it > 0 }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.BLACK
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = NearBlack, surface = NearBlack)) {
                TargetsApp(
                    requestedTargetId = targetFromNotification,
                    onRequestHandled = { targetFromNotification = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        targetFromNotification = intent.getLongExtra("target_id", -1).takeIf { it > 0 }
    }
}

@Composable
private fun TargetsApp(
    requestedTargetId: Long?,
    onRequestHandled: () -> Unit,
    vm: TargetsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val ui by vm.state.collectAsState()
    var alertsEnabled by remember { mutableStateOf(TargetAlerts.isEnabled(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            TargetAlerts.setEnabled(context, true)
            alertsEnabled = true
            Toast.makeText(context, "Уведомления включены", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Уведомления не разрешены", Toast.LENGTH_SHORT).show()
        }
    }
    val onToggleAlerts = {
        if (alertsEnabled) {
            TargetAlerts.setEnabled(context, false)
            alertsEnabled = false
            Toast.makeText(context, "Уведомления выключены", Toast.LENGTH_SHORT).show()
        } else if (!TargetAlerts.hasPermission(context) && Build.VERSION.SDK_INT >= 33) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            TargetAlerts.setEnabled(context, true)
            alertsEnabled = true
            Toast.makeText(context, "Уведомления включены", Toast.LENGTH_SHORT).show()
        }
    }

    Box(Modifier.fillMaxSize().background(NearBlack)) {
        if (ui.targets.isEmpty()) {
            EmptyScreen(
                loading = ui.loading,
                error = ui.error,
                onRetry = vm::refresh,
                modifier = Modifier.align(Alignment.Center)
            )
            TopBar(
                alertsEnabled, ui.refreshing,
                onToggleAlerts, vm::refresh,
                Modifier.align(Alignment.TopCenter)
            )
        } else {
            val targets = ui.targets
            key(targets.size) {
                val initialPage = if (targets.size > 1) {
                    val base = 1_000_000
                    base - (base % targets.size)
                } else 0
                val pageCount = if (targets.size > 1) Int.MAX_VALUE else 1
                val pager = rememberPagerState(initialPage = initialPage) { pageCount }
                val scope = rememberCoroutineScope()
                val currentIndex = Math.floorMod(pager.currentPage, targets.size)
                LaunchedEffect(requestedTargetId, targets) {
                    val requestedIndex = targets.indexOfFirst { it.id == requestedTargetId }
                    if (requestedTargetId != null && requestedIndex >= 0) {
                        val current = Math.floorMod(pager.currentPage, targets.size)
                        val forward = (requestedIndex - current + targets.size) % targets.size
                        val offset = if (forward > targets.size / 2) forward - targets.size else forward
                        pager.animateScrollToPage(pager.currentPage + offset)
                        onRequestHandled()
                    }
                }
                val now by produceState(initialValue = Instant.now()) {
                    while (true) {
                        value = Instant.now()
                        delay(1000)
                    }
                }
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = targets.size > 1,
                    contentPadding = PaddingValues(0.dp),
                ) { page ->
                    val target = targets[Math.floorMod(page, targets.size)]
                    TargetSlide(
                        target = target,
                        now = now,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                TopBar(
                    alertsEnabled, ui.refreshing,
                    onToggleAlerts, vm::refresh,
                    Modifier.align(Alignment.TopCenter)
                )
                PagerFooter(
                    targets = targets,
                    index = currentIndex,
                    onPageClick = { index ->
                        val forward = (index - currentIndex + targets.size) % targets.size
                        val offset = if (forward > targets.size / 2) forward - targets.size else forward
                        scope.launch { pager.animateScrollToPage(pager.currentPage + offset) }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
                if (ui.error != null) {
                    Text(
                        "Нет связи. Показаны сохранённые данные",
                        modifier = Modifier.align(Alignment.TopCenter)
                            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 70.dp)
                            .background(Color.Black.copy(alpha = .65f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        fontSize = 11.sp, color = Gold
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetSlide(target: ElusiveTarget, now: Instant, modifier: Modifier = Modifier) {
    val status = target.status(now)
    val faded = status != TargetStatus.ACTIVE
    val context = LocalContext.current
    val imageModel = remember(target.imageUrl) {
        ImageRequest.Builder(context)
            .data(target.imageUrl)
            .crossfade(350)
            .build()
    }
    val greyscale = remember {
        ColorMatrix().apply { setToSaturation(0f) }
    }

    Box(modifier.background(NearBlack)) {
        AsyncImage(
            model = imageModel,
            contentDescription = "Портрет цели: ${target.name}",
            contentScale = ContentScale.Crop,
            colorFilter = if (faded) ColorFilter.colorMatrix(greyscale) else null,
            modifier = Modifier.fillMaxSize(),
        )
        if (faded) {
            Box(Modifier.fillMaxSize().background(Color(0xFF070809).copy(alpha = .38f)))
        }
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = .72f),
                .23f to Color.Black.copy(alpha = .08f),
                .40f to Color.Transparent,
                .55f to Color.Black.copy(alpha = .16f),
                .75f to Color.Black.copy(alpha = .83f),
                1f to NearBlack,
            )
        ))
        // The bottom panel deliberately lives above the photo and under the pager dots.
        Column(
            modifier = Modifier.align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 26.dp, end = 26.dp, bottom = 132.dp),
        ) {
            StatusPill(status)
            Spacer(Modifier.height(16.dp))
            Text(
                "НЕУЛОВИМАЯ ЦЕЛЬ  /  ${target.location}",
                color = Pale.copy(alpha = .72f),
                fontSize = 10.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Text(
                target.name.uppercase(Locale.ROOT),
                color = Pale,
                fontSize = 36.sp,
                lineHeight = 39.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-.6).sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (status == TargetStatus.ACTIVE) Icons.Outlined.Schedule else Icons.Outlined.TrackChanges,
                    contentDescription = null,
                    tint = when (status) {
                        TargetStatus.ACTIVE -> Green
                        TargetStatus.UPCOMING -> Gold
                        TargetStatus.EXPIRED -> Muted
                    },
                    modifier = Modifier.size(43.dp)
                )
                Spacer(Modifier.width(15.dp))
                Column {
                    Text(
                        when (status) {
                            TargetStatus.ACTIVE -> "ДО ЗАВЕРШЕНИЯ МИССИИ"
                            TargetStatus.UPCOMING -> "ДО НАЧАЛА МИССИИ"
                            TargetStatus.EXPIRED -> "СРОК МИССИИ ИСТЁК"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.35.sp,
                        color = Pale.copy(alpha = .78f)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (status == TargetStatus.EXPIRED) "00:00:00" else formatCountdown(
                            now, if (status == TargetStatus.ACTIVE) target.endsAt else target.startsAt
                        ),
                        color = if (status == TargetStatus.EXPIRED) Muted else Pale,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Light,
                        fontSize = 39.sp,
                        letterSpacing = (-1.6).sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "${formatDate(target.startsAt)}  —  ${formatDate(target.endsAt)}",
                fontSize = 11.sp,
                letterSpacing = .6.sp,
                color = Pale.copy(alpha = .6f),
                fontFamily = FontFamily.SansSerif,
            )
        }
    }
}

@Composable
private fun StatusPill(status: TargetStatus) {
    val (title, accent) = when (status) {
        TargetStatus.ACTIVE -> "●  ЦЕЛЬ АКТИВНА" to Green
        TargetStatus.UPCOMING -> "◌  СКОРО ПОЯВИТСЯ" to Gold
        TargetStatus.EXPIRED -> "—  НЕДОСТУПНА" to Muted
    }
    Box(
        Modifier.clip(RoundedCornerShape(3.dp))
            .background(accent.copy(alpha = .15f))
            .padding(horizontal = 13.dp, vertical = 8.dp)
    ) {
        Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, color = accent)
    }
}

@Composable
private fun TopBar(
    alertsEnabled: Boolean,
    refreshing: Boolean,
    onToggleAlerts: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Row(
        modifier = modifier.fillMaxWidth()
            .padding(top = topInset + 10.dp, start = 22.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).drawBehind {
                drawCircle(Scarlet, style = Stroke(width = 2.2.dp.toPx()))
                drawLine(Scarlet, center.copy(y = 0f), center.copy(y = size.height), 1.7.dp.toPx())
                drawLine(Scarlet, center.copy(x = 0f), center.copy(x = size.width), 1.7.dp.toPx())
                drawCircle(Scarlet, radius = 3.dp.toPx())
            }
        )
        Spacer(Modifier.width(13.dp))
        Column {
            Text("HITMAPS", fontSize = 16.sp, letterSpacing = 2.1.sp, fontWeight = FontWeight.Black, color = Pale)
            Text("ELUSIVE TARGET TRACKER", fontSize = 8.sp, letterSpacing = 1.2.sp, color = Pale.copy(alpha = .58f))
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onRefresh, enabled = !refreshing) {
            if (refreshing) {
                CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 1.7.dp, color = Pale)
            } else {
                Icon(Icons.Outlined.Refresh, contentDescription = "Обновить цели", tint = Pale, modifier = Modifier.size(23.dp))
            }
        }
        IconButton(onClick = onToggleAlerts) {
            Icon(
                if (alertsEnabled) Icons.Outlined.Notifications else Icons.Outlined.NotificationsOff,
                contentDescription = if (alertsEnabled) "Отключить уведомления" else "Включить уведомления",
                tint = if (alertsEnabled) Green else Pale.copy(alpha = .75f),
                modifier = Modifier.size(23.dp)
            )
        }
    }
}

@Composable
private fun PagerFooter(
    targets: List<ElusiveTarget>,
    index: Int,
    onPageClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(
        modifier = modifier.fillMaxWidth().padding(bottom = navInset + 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 26.dp)
                .height(1.dp).background(Pale.copy(alpha = .16f))
        )
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "%02d  /  %02d".format(Locale.ROOT, index + 1, targets.size),
                color = Pale.copy(alpha = .75f), fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                targets.forEachIndexed { i, _ ->
                    Box(
                        modifier = Modifier.size(width = if (index == i) 30.dp else 18.dp, height = 28.dp)
                            .clickable { onPageClick(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(width = if (index == i) 23.dp else 7.dp, height = 7.dp)
                                .clip(CircleShape)
                                .background(if (i == index) Scarlet else Pale.copy(alpha = .38f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyScreen(
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.TrackChanges, null, modifier = Modifier.size(66.dp), tint = Scarlet)
        Spacer(Modifier.height(23.dp))
        Text("НЕУЛОВИМЫЕ ЦЕЛИ", color = Pale, fontSize = 24.sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, letterSpacing = 1.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            when {
                loading -> "Получаем актуальное расписание..."
                error != null -> "Нет подключения к Hitmaps. Проверь интернет и попробуй ещё раз."
                else -> "Сейчас в расписании нет целей. Загляни позже."
            },
            color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 22.sp
        )
        Spacer(Modifier.height(25.dp))
        if (loading) {
            CircularProgressIndicator(color = Scarlet, modifier = Modifier.size(27.dp), strokeWidth = 2.dp)
        } else {
            Surface(
                shape = RoundedCornerShape(5.dp),
                color = Scarlet,
                modifier = Modifier.clickable { onRetry() }
            ) {
                Text("ОБНОВИТЬ", color = Color.White, fontWeight = FontWeight.Bold,
                    letterSpacing = 1.3.sp, fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 23.dp, vertical = 14.dp))
            }
        }
    }
}

private val dateFormatter = DateTimeFormatter.ofPattern("dd MMM HH:mm", Locale.forLanguageTag("ru"))
private fun formatDate(instant: Instant): String = dateFormatter
    .withZone(ZoneId.systemDefault())
    .format(instant)
    .uppercase(Locale.forLanguageTag("ru"))
