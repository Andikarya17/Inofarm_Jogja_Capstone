export interface KotlinCodeFile {
  filename: string;
  category: 'Screens' | 'UI Theme' | 'Model & Logic' | 'Config';
  description: string;
  code: string;
}

export const KOTLIN_CODE_FILES: KotlinCodeFile[] = [
  {
    filename: 'DashboardScreen.kt',
    category: 'Screens',
    description: 'Jetpack Compose Beranda Dashboard: Hero phase banner, 2x2 Bento sensor cards, Canvas cubic Bezier chart, dan status validasi terakhir.',
    code: `package com.inofarm.jogja.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inofarm.jogja.ui.theme.*

@Composable
fun DashboardScreen(
    onNavigateToHistory: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedChartMetric by remember { mutableStateOf("Suhu Air") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section Header
        Column {
            Text(
                text = "Dashboard Pengelola",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Ringkasan kondisi hidroponik saat ini.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }

        // Hero Card: Fase Tanaman & Status IoT
        PlantPhaseHeroCard()

        // 2x2 Bento Grid: Sensor Readings
        SensorBentoGrid(
            waterTemp = 24.5f,
            phValue = 6.2f,
            roomTemp = 28.0f,
            humidity = 72
        )

        // Sensor Trend Chart (Canvas Cubic Bezier)
        SensorHistoryChartCard(
            selectedMetric = selectedChartMetric,
            onMetricSelect = { selectedChartMetric = it }
        )

        // Last Manual Nutrient Input Card
        LatestNutrientInputCard(
            time = "08:05 WIB",
            ppm = 850,
            statusText = "Status: Tepat Waktu & Valid"
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun PlantPhaseHeroCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFDEE9FC) // surface-container-high
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = "FASE TANAMAN",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "VEGETATIF",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = InoPrimary
                )
            )
            Spacer(modifier = Modifier.height(14.dp))

            // IoT Status Chip with Pulse Dot
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, Color(0xFFBBCABF).copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 0.8f,
                        targetValue = 1.3f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "scale"
                    )

                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(InoPrimaryContainer, CircleShape)
                    )
                    Text(
                        text = "Status Perangkat IoT: Aktif (Online)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun SensorBentoGrid(
    waterTemp: Float,
    phValue: Float,
    roomTemp: Float,
    humidity: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SensorCard(
                title = "Suhu Air",
                value = "%.1f".format(waterTemp),
                unit = "°C",
                icon = Icons.Outlined.WaterDrop,
                iconColor = InoSecondary,
                modifier = Modifier.weight(1f)
            )
            SensorCard(
                title = "pH Larutan",
                value = "%.1f".format(phValue),
                unit = "",
                icon = Icons.Outlined.Science,
                iconColor = InoPrimary,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SensorCard(
                title = "Suhu Ruang",
                value = "%.1f".format(roomTemp),
                unit = "°C",
                icon = Icons.Outlined.DeviceThermostat,
                iconColor = Color(0xFFA43A3A),
                modifier = Modifier.weight(1f)
            )
            SensorCard(
                title = "Kelembapan",
                value = humidity.toString(),
                unit = "%",
                icon = Icons.Outlined.Air,
                iconColor = Color(0xFF2170E4),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun SensorCard(
    title: String,
    value: String,
    unit: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(112.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFBBCABF).copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFEFF4FF),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.padding(6.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color(0xFF3C4A42),
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF121C2A)
                    )
                )
                if (unit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFF3C4A42)
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun SensorHistoryChartCard(
    selectedMetric: String,
    onMetricSelect: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFBBCABF).copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Grafik Riwayat Sensor",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF121C2A)
                    )
                )

                // Metric Toggle Chips
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Suhu Air", "pH").forEach { metric ->
                        val isSelected = selectedMetric == metric
                        FilterChip(
                            selected = isSelected,
                            onClick = { onMetricSelect(metric) },
                            label = { Text(metric, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = InoPrimaryContainer,
                                selectedLabelColor = Color.White
                            ),
                            shape = CircleShape
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Smooth Bezier Curve Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                val width = size.width
                val height = size.height

                val path = Path().apply {
                    moveTo(0f, height * 0.75f)
                    cubicTo(
                        width * 0.25f, height * 0.2f,
                        width * 0.45f, height * 0.85f,
                        width * 0.65f, height * 0.4f
                    )
                    cubicTo(
                        width * 0.8f, height * 0.1f,
                        width * 0.9f, height * 0.15f,
                        width, height * 0.6f
                    )
                }

                // Fill Gradient
                val fillPath = Path().apply {
                    addPath(path)
                    lineTo(width, height)
                    lineTo(0f, height)
                    close()
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            InoPrimaryContainer.copy(alpha = 0.35f),
                            InoPrimaryContainer.copy(alpha = 0.0f)
                        )
                    )
                )

                // Stroke Line
                drawPath(
                    path = path,
                    color = InoPrimaryContainer,
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // X-Axis Time labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("04:00", "05:00", "06:00", "07:00", "08:00").forEach { time ->
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF6C7A71)
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun LatestNutrientInputCard(
    time: String,
    ppm: Int,
    statusText: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFBBCABF).copy(alpha = 0.5f))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Input Nutrisi Manual Terakhir",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF121C2A)
                    )
                )
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Menu",
                        tint = Color(0xFF6C7A71)
                    )
                }
            }

            Divider(color = Color(0xFFBBCABF).copy(alpha = 0.3f))

            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEFF4FF),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Eco,
                            contentDescription = null,
                            tint = InoPrimary,
                            modifier = Modifier.padding(8.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Hari ini",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF121C2A)
                            )
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                text = "Waktu Input: $time",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6C7A71))
                            )
                            Text(
                                text = "Nilai PPM: $ppm",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6C7A71))
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = CircleShape,
                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color(0xFF006C49), CircleShape)
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF006C49)
                            )
                        )
                    }
                }
            }
        }
    }
}
`,
  },
  {
    filename: 'HistoryScreen.kt',
    category: 'Screens',
    description: 'Jetpack Compose Riwayat Pemberian Nutrisi: Tombol Filter, Ekspor CSV, Summary chips (Total/Valid/Abnormal), dan list kartu riwayat berstatus dinamis.',
    code: `package com.inofarm.jogja.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inofarm.jogja.data.model.NutrientLog
import com.inofarm.jogja.ui.theme.*

@Composable
fun HistoryScreen(
    logs: List<NutrientLog>,
    onFilterClick: () -> Unit = {},
    onExportClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val totalCount = logs.size
    val validCount = logs.count { it.status == "valid" }
    val abnormalCount = logs.count { it.status != "valid" }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        // Title & Description
        Text(
            text = "Riwayat Pemberian Nutrisi",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        )
        Text(
            text = "Lacak dan validasi pencatatan nutrisi harian.",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Actions Row (Filter & Ekspor Buttons)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onFilterClick,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(2.dp, InoPrimary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = InoPrimary)
            ) {
                Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Filter", fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = onExportClick,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(2.dp, InoPrimary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = InoPrimary)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ekspor", fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Summary Row (Chips)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryChip(text = "Total: $totalCount", textColor = Color(0xFF121C2A), bgColor = Color(0xFFEFF4FF))
            SummaryChip(text = "Valid: $validCount", textColor = InoPrimary, bgColor = Color(0xFF10B981).copy(alpha = 0.15f))
            SummaryChip(text = "Abnormal: $abnormalCount", textColor = Color(0xFFBA1A1A), bgColor = Color(0xFFFFDAD6))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // LazyColumn List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(logs, key = { it.id }) { log ->
                NutrientLogItemCard(log)
            }
        }
    }
}

@Composable
fun SummaryChip(text: String, textColor: Color, bgColor: Color) {
    Surface(
        shape = CircleShape,
        color = bgColor,
        border = BorderStroke(1.dp, textColor.copy(alpha = 0.3f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
        )
    }
}

@Composable
fun NutrientLogItemCard(log: NutrientLog) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFBBCABF).copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Timestamp
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = Color(0xFF6C7A71),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "\${log.dateStr}, \${log.timeStr}",
                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6C7A71))
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // PPM Value
            Text(
                text = "PPM Manual: \${log.ppm}",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF121C2A)
                )
            )

            // Phase
            Text(
                text = "Fase: \${log.phase.replaceFirstChar { it.uppercase() }}",
                style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF3C4A42))
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Semantic Status Badge
            when (log.status) {
                "valid" -> StatusBadge(
                    label = "Sudah Tervalidasi",
                    textColor = Color(0xFF006C49),
                    bgColor = Color(0xFF10B981).copy(alpha = 0.15f),
                    icon = Icons.Default.CheckCircle
                )
                "late" -> StatusBadge(
                    label = "Terlambat Dicatat",
                    textColor = Color(0xFFBA1A1A),
                    bgColor = Color(0xFFFFDAD6),
                    icon = Icons.Default.Error
                )
                "low" -> StatusBadge(
                    label = "PPM Rendah",
                    textColor = Color(0xFF854D0E),
                    bgColor = Color(0xFFFEF08A).copy(alpha = 0.35f),
                    icon = Icons.Default.Warning
                )
                "high" -> StatusBadge(
                    label = "PPM Tinggi",
                    textColor = Color(0xFF9A3412),
                    bgColor = Color(0xFFFED7AA).copy(alpha = 0.4f),
                    icon = Icons.Default.ReportProblem
                )
            }
        }
    }
}

@Composable
fun StatusBadge(
    label: String,
    textColor: Color,
    bgColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Surface(
        shape = CircleShape,
        color = bgColor,
        border = BorderStroke(1.dp, textColor.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, color = textColor))
        }
    }
}
`,
  },
  {
    filename: 'SettingsScreen.kt',
    category: 'Screens',
    description: 'Jetpack Compose Pengaturan Parameter Validasi: Jadwal nutrisi (pagi/sore/toleransi), Rentang PPM ideal segmented button, dan RangeSlider ambang batas sensor.',
    code: `package com.inofarm.jogja.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inofarm.jogja.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onSaveSuccess: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var morningTime by remember { mutableStateOf("08:00") }
    var afternoonTime by remember { mutableStateOf("16:00") }
    var toleranceMin by remember { mutableStateOf("15") }

    var selectedPhase by remember { mutableStateOf("Vegetatif") }
    var minPpm by remember { mutableStateOf("800") }
    var maxPpm by remember { mutableStateOf("1000") }

    var waterTempRange by remember { mutableStateOf(20f..28f) }
    var phRange by remember { mutableStateOf(5.5f..6.5f) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        Column {
            Text(
                text = "Pengaturan Parameter Validasi",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Sesuaikan nilai ambang batas sistem.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }

        // Section 1: Jadwal Nutrisi
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFBBCABF).copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null, tint = InoPrimary)
                    Text("Jadwal Nutrisi", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = morningTime,
                    onValueChange = { morningTime = it },
                    label = { Text("JADWAL PAGI") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = afternoonTime,
                    onValueChange = { afternoonTime = it },
                    label = { Text("JADWAL SORE") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = toleranceMin,
                    onValueChange = { toleranceMin = it },
                    label = { Text("TOLERANSI (MENIT)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        // Section 2: Rentang PPM Ideal
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFBBCABF).copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.WaterDrop, contentDescription = null, tint = InoPrimary)
                        Text("Rentang PPM Ideal", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Phase Segmented Toggle
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFD9E3F6),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(4.dp)) {
                        listOf("Vegetatif", "Generatif").forEach { phase ->
                            val isSelected = selectedPhase == phase
                            Button(
                                onClick = {
                                    selectedPhase = phase
                                    if (phase == "Vegetatif") {
                                        minPpm = "800"
                                        maxPpm = "1000"
                                    } else {
                                        minPpm = "1100"
                                        maxPpm = "1400"
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) Color.White else Color.Transparent,
                                    contentColor = if (isSelected) Color(0xFF121C2A) else Color(0xFF3C4A42)
                                ),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = if (isSelected) 2.dp else 0.dp)
                            ) {
                                Text(phase, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = minPpm,
                        onValueChange = { minPpm = it },
                        label = { Text("BATAS BAWAH") },
                        trailingIcon = { Text("PPM", fontSize = 12.sp, color = Color(0xFF6C7A71)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = maxPpm,
                        onValueChange = { maxPpm = it },
                        label = { Text("BATAS ATAS") },
                        trailingIcon = { Text("PPM", fontSize = 12.sp, color = Color(0xFF6C7A71)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        }

        // Section 3: Ambang Batas Sensor (Dual Range Slider)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFBBCABF).copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.Sensors, contentDescription = null, tint = InoPrimary)
                    Text("Ambang Batas Sensor", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Suhu Air Range
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Suhu Air", fontWeight = FontWeight.SemiBold)
                    Text(
                        "\${waterTempRange.start.toInt()} - \${waterTempRange.endInclusive.toInt()} °C",
                        fontWeight = FontWeight.Bold,
                        color = InoPrimary
                    )
                }
                RangeSlider(
                    value = waterTempRange,
                    onValueChange = { waterTempRange = it },
                    valueRange = 0f..50f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = InoPrimary,
                        inactiveTrackColor = Color(0xFFD9E3F6)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // pH Range
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("pH Larutan", fontWeight = FontWeight.SemiBold)
                    Text(
                        "%.1f - %.1f".format(phRange.start, phRange.endInclusive),
                        fontWeight = FontWeight.Bold,
                        color = InoPrimary
                    )
                }
                RangeSlider(
                    value = phRange,
                    onValueChange = { phRange = it },
                    valueRange = 0f..14f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = InoPrimary,
                        inactiveTrackColor = Color(0xFFD9E3F6)
                    )
                )
            }
        }

        // Save Button
        Button(
            onClick = onSaveSuccess,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = InoPrimaryContainer,
                contentColor = Color.White
            )
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Simpan Pengaturan", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
`,
  },
  {
    filename: 'MainActivity.kt',
    category: 'Screens',
    description: 'Android Entry Point: Setup Scaffolding, TopAppBar, Fixed BottomNavBar, dan Navigation Controller antar layar.',
    code: `package com.inofarm.jogja

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.*
import com.inofarm.jogja.ui.screens.*
import com.inofarm.jogja.ui.theme.InofarmTheme
import com.inofarm.jogja.ui.theme.InoPrimary
import com.inofarm.jogja.ui.theme.InoPrimaryContainer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            InofarmTheme {
                InofarmMainApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InofarmMainApp() {
    val navController = rememberNavController()
    var currentRoute by remember { mutableStateOf("beranda") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.padding(start = 4.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.Eco, contentDescription = null, tint = InoPrimary)
                        Text(
                            text = "Inofarm Jogja",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = InoPrimary
                            )
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        BadgedBox(badge = { Badge { Text("1") } }) {
                            Icon(Icons.Default.Notifications, contentDescription = "Notifikasi")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                val items = listOf(
                    Triple("beranda", "Beranda", Icons.Default.Home),
                    Triple("riwayat", "Riwayat", Icons.Default.History),
                    Triple("pengaturan", "Pengaturan", Icons.Default.Settings)
                )

                items.forEach { (route, label, icon) ->
                    val selected = currentRoute == route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            currentRoute = route
                            navController.navigate(route) {
                                popUpTo("beranda") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = InoPrimary,
                            indicatorColor = InoPrimaryContainer
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "beranda",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("beranda") {
                DashboardScreen(onNavigateToHistory = {
                    currentRoute = "riwayat"
                    navController.navigate("riwayat")
                })
            }
            composable("riwayat") {
                HistoryScreen(logs = emptyList())
            }
            composable("pengaturan") {
                SettingsScreen()
            }
        }
    }
}
`,
  },
  {
    filename: 'Theme.kt',
    category: 'UI Theme',
    description: 'Material 3 Color Scheme & Tokens sesuai spesifikasi design token Inofarm Jogja / Agro-Intelligence System.',
    code: `package com.inofarm.jogja.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val InoPrimary = Color(0xFF006C49)
val InoPrimaryContainer = Color(0xFF10B981)
val InoOnPrimary = Color(0xFFFFFFFF)
val InoSecondary = Color(0xFF0058BE)
val InoBackground = Color(0xFFF8F9FF)
val InoSurface = Color(0xFFF8F9FF)
val InoOnSurface = Color(0xFF121C2A)
val InoOnSurfaceVariant = Color(0xFF3C4A42)
val InoOutlineVariant = Color(0xFFBBCABF)
val InoError = Color(0xFFBA1A1A)

private val LightColorScheme = lightColorScheme(
    primary = InoPrimary,
    onPrimary = InoOnPrimary,
    primaryContainer = InoPrimaryContainer,
    onPrimaryContainer = Color(0xFF00422B),
    secondary = InoSecondary,
    onSecondary = Color(0xFFFFFFFF),
    background = InoBackground,
    onBackground = InoOnSurface,
    surface = InoSurface,
    onSurface = InoOnSurface,
    surfaceVariant = Color(0xFFD9E3F6),
    onSurfaceVariant = InoOnSurfaceVariant,
    outline = Color(0xFF6C7A71),
    outlineVariant = InoOutlineVariant,
    error = InoError
)

@Composable
fun InofarmTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
`,
  },
  {
    filename: 'NutrientValidator.kt',
    category: 'Model & Logic',
    description: 'Logika bisnis validasi otomatis waktu toleransi & rentang PPM hidroponik melon (Vegetatif vs Generatif).',
    code: `package com.inofarm.jogja.data.logic

import com.inofarm.jogja.data.model.NutrientLog
import com.inofarm.jogja.data.model.ValidationResult
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs

class NutrientValidator {

    fun validate(
        inputTime: LocalTime,
        ppm: Int,
        phase: String, // "Vegetatif" or "Generatif"
        morningSchedule: LocalTime = LocalTime.of(8, 0),
        afternoonSchedule: LocalTime = LocalTime.of(16, 0),
        toleranceMinutes: Long = 15L
    ): ValidationResult {
        // 1. Validasi Toleransi Waktu
        val diffMorning = abs(ChronoUnit.MINUTES.between(inputTime, morningSchedule))
        val diffAfternoon = abs(ChronoUnit.MINUTES.between(inputTime, afternoonSchedule))
        val minDiff = minOf(diffMorning, diffAfternoon)
        val isTimeValid = minDiff <= toleranceMinutes

        // 2. Ambang Batas PPM sesuai Fase
        val (minPpm, maxPpm) = if (phase.equals("Vegetatif", ignoreCase = true)) {
            800 to 1000
        } else {
            1100 to 1400
        }

        return when {
            ppm < minPpm -> ValidationResult(
                status = "low",
                label = "PPM Rendah",
                isValid = false,
                recommendation = "Konsentrasi PPM ($ppm) di bawah ambang batas minimal ($minPpm PPM). Tambahkan nutrisi AB Mix pekatan A & B secara berimbang."
            )
            ppm > maxPpm -> ValidationResult(
                status = "high",
                label = "PPM Tinggi",
                isValid = false,
                recommendation = "Konsentrasi PPM ($ppm) melampaui batas optimal ($maxPpm PPM). Tambahkan air baku tandon untuk menurunkan EC/PPM."
            )
            !isTimeValid -> ValidationResult(
                status = "late",
                label = "Terlambat Dicatat",
                isValid = false,
                recommendation = "Pencatatan dilakukan di luar toleransi ±$toleranceMinutes menit dari jadwal ($morningSchedule & $afternoonSchedule)."
            )
            else -> ValidationResult(
                status = "valid",
                label = "Sudah Tervalidasi",
                isValid = true,
                recommendation = "Kondisi larutan nutrisi melon optimal dan siap diserap."
            )
        }
    }
}
`,
  },
  {
    filename: 'build.gradle.kts',
    category: 'Config',
    description: 'Gradle configuration dependencies untuk Jetpack Compose, Material 3, Navigation, Coroutines & ViewModel.',
    code: `plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.inofarm.jogja"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.inofarm.jogja"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Jetpack Compose & Material 3
    implementation(platform(libs.androidx.compose.bom))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Lifecycle & ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
}
`,
  },
];
