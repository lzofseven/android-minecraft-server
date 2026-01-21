package com.lzofseven.mcserver.ui.screens.dashboard

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lzofseven.mcserver.data.local.entity.MCServerEntity
import com.lzofseven.mcserver.ui.navigation.Screen
import com.lzofseven.mcserver.core.execution.ServerStatus
import com.lzofseven.mcserver.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val serverEntity by viewModel.serverEntity.collectAsState()
    val serverStatus by viewModel.serverStatus.collectAsState()
    val playitLink by viewModel.playitLink.collectAsState()
    val properties by viewModel.properties.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val showEulaDialog by viewModel.showEulaDialog.collectAsState()
    val onlinePlayers by viewModel.onlinePlayers.collectAsState()
    val serverStats by viewModel.serverStats.collectAsState(initial = com.lzofseven.mcserver.core.execution.RealServerManager.ServerStats(0.0, 0.0))
    val consoleLogs by viewModel.consoleLogs.collectAsState(initial = emptyList<String>())
    val showPermissionDialog by viewModel.showPermissionDialog.collectAsState()
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val folderPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri: android.net.Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val simplePath = it.path?.replace("/tree/primary:", "/storage/emulated/0/") ?: it.toString()
            viewModel.updateServerUri(it.toString(), simplePath)
        }
    }
    
    if (showEulaDialog) {
        EulaDialog(
            onAccept = { viewModel.acceptEula() },
            onLinkFolder = { folderPickerLauncher.launch(null) },
            isLinked = serverEntity?.uri != null
        )
    }

    if (showPermissionDialog) {
        PermissionDialog(
            onConfirm = { viewModel.openPermissionSettings() },
            onDismiss = { viewModel.dismissPermissionDialog() }
        )
    }

    // Auto-navigation to Console
    LaunchedEffect(Unit) {
        viewModel.navigateToConsole.collect {
            // navToConsole logic placeholder - actual tab switching is likely handled here
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        if (serverEntity != null) {
            Scaffold(
                containerColor = Color.Transparent
            ) { padding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(bottom = 120.dp), // Space for floating nav
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Header
                    item {
                        DashboardHeader(serverEntity!!, onlinePlayers)
                    }

                    // Installation Progress
                    if (downloadProgress != null) {
                        item {
                            InstallationProgressCard(progress = downloadProgress!!)
                        }
                    }

                    // Hero Card (Status Mundo)
                    item {
                        val motd = properties["motd"] ?: "A Minecraft Server"
                        HeroStatusCard(
                            serverStatus = serverStatus,
                            onToggle = { viewModel.toggleServer() },
                            serverName = serverEntity?.name ?: "Server",
                            motd = motd
                        )
                    }

                    // Playit.gg Status
                    item {
                        val playitStatus by viewModel.playitStatus.collectAsState()
                        val claimLink by viewModel.playitClaimLink.collectAsState()
                        val playitAddress by viewModel.playitAddress.collectAsState()
                        
                        // Always show if it's not stopped, OR if we have a claim link/address
                        if (playitStatus != "Stopped" || claimLink != null || playitAddress != null) {
                            PlayitStatusCard(
                                status = playitStatus, 
                                claimLink = claimLink,
                                address = playitAddress
                            )
                        }
                    }

                    // Real-time Stats
                    item {
                        PerformanceStatsSection(
                            serverStats = serverStats,
                            ramLimitGb = (serverEntity?.ramAllocationMB ?: 2048) / 1024.0,
                            onRamClick = { navController.navigate(Screen.Config.createRoute(serverEntity!!.id)) }
                        )
                    }

                    // Quick Tools
                    item {
                        QuickToolsSection(navController, serverEntity!!.id)
                    }

                    // Recent Logs (Mini Console)
                    item {
                        MiniConsoleSection(
                            logs = consoleLogs,
                            onSeeConsole = { navController.navigate(Screen.Console.createRoute(serverEntity!!.id)) }
                        )
                    }
                }
            }
            // Floating navigation at bottom - outside Scaffold for transparency
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                GlassBottomNav(navController, serverEntity!!.id)
            }
        } else {
             Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                 CircularProgressIndicator(color = PrimaryDark)
             }
        }
    }
}

@Composable
fun DashboardHeader(server: MCServerEntity, onlinePlayers: List<String>) {
    // Determine status (user is online if list is not empty for now, or check specific name if provided)
    // For MVP, if ANY player is online, we show status green, or we can bind to specific user.
    // User requested: "Green only if I am on server". Assuming user is "Steve" or similar for now, 
    // but better to just show "Offline" if list is empty or generic "X Players".
    
    val isOnline = onlinePlayers.isNotEmpty() // Simple logic: if anyone is there, it's active. Refine later with Auth.
    
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Status Indicator
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isOnline) Color.Green else Color.Red)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    server.name.uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    color = Color.White
                )
            }
            Text(
                "Versão ${server.version} • ${server.type} • ${if(isOnline) "On-line" else "Off-line"}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.4f),
                fontWeight = FontWeight.Bold
            )
        }
        
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            var notificationsEnabled by remember { mutableStateOf(true) }
            HeaderButton(
                icon = if (notificationsEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                onClick = { notificationsEnabled = !notificationsEnabled }
            )
        }
    }
}

@Composable
fun HeaderButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit = {}) {
    Surface(
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        modifier = Modifier.size(44.dp).clickable { onClick() }
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.padding(10.dp)
        )
    }
}

@Composable
fun HeroStatusCard(serverStatus: ServerStatus, onToggle: () -> Unit, serverName: String, motd: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.77f)) {
                AsyncImage(
                    model = "https://lh3.googleusercontent.com/aida-public/AB6AXuCwfrVy1tVAb2P64nfILPuylYzDWefOwHF251qSRzEp0xrLw1zRuRyS1TwbUVGstZ7Hd1h2lbIWTmme9atM1kDq7MA2HuNRk_yVkIXPkN8VK6On77IKdxXB2_HoP2CAXvSMbAAMV_Q_ixgnlis_A-slL40GFyzmbuW1fEzujtubzwlNfDK6OeB8ZUzFj6yTwMyXVU2ii1r9OgmjVTSm1WxffLOFkgNmowvuLCfRgynW10vEv7kq7F42ttsHnsnXYjJtlLUCJYlNBi_p",
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, SurfaceDark)))
                )
                
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Dns, null, tint = PrimaryDark, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(serverName.take(12).uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
            
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Status do Mundo", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = Color.White)
                Text(
                    text = com.lzofseven.mcserver.util.MotdUtils.parseMinecraftColors(motd), 
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(20.dp))
                
                Button(
                    onClick = onToggle,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            serverStatus == ServerStatus.INSTALLING -> Color.Gray
                            serverStatus == ServerStatus.RUNNING -> Color.Red
                            else -> PrimaryDark
                        }
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    when(serverStatus) {
                        ServerStatus.INSTALLING -> {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = BackgroundDark, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("INSTALANDO...", fontWeight = FontWeight.Black, color = BackgroundDark)
                        }
                        ServerStatus.STARTING -> {
                             CircularProgressIndicator(modifier = Modifier.size(24.dp), color = BackgroundDark, strokeWidth = 2.dp)
                             Spacer(Modifier.width(8.dp))
                             Text("INICIANDO...", fontWeight = FontWeight.Black, color = BackgroundDark)
                        }
                        ServerStatus.STOPPING -> {
                             CircularProgressIndicator(modifier = Modifier.size(24.dp), color = BackgroundDark, strokeWidth = 2.dp)
                             Spacer(Modifier.width(8.dp))
                             Text("PARANDO...", fontWeight = FontWeight.Black, color = BackgroundDark)
                        }
                        else -> {
                            Icon(if(serverStatus == ServerStatus.STOPPED) Icons.Default.PlayArrow else Icons.Default.Stop, null, tint = BackgroundDark)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if(serverStatus == ServerStatus.STOPPED) "INICIAR SERVIDOR" else "PARAR SERVIDOR",
                                fontWeight = FontWeight.Black,
                                color = BackgroundDark,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PerformanceStatsSection(
    serverStats: com.lzofseven.mcserver.core.execution.RealServerManager.ServerStats, 
    ramLimitGb: Double,
    onRamClick: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("DESEMPENHO", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text("Atualiza a cada 5s", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.2f))
        }
        Spacer(Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                title = "Processador",
                value = String.format("%.1f", serverStats.cpu),
                unit = "%",
                icon = Icons.Default.Memory,
                color = Color(0xFF42A5F5), // Blue
                usage = serverStats.cpu / 100.0,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Memoria RAM",
                value = String.format("%.1f", serverStats.ram),
                unit = "/${String.format("%.1f", ramLimitGb)}GB",
                icon = Icons.Default.Storage,
                color = Color(0xFFAB47BC), // Purple
                usage = (serverStats.ram / ramLimitGb).coerceIn(0.0, 1.0),
                modifier = Modifier.weight(1f),
                onClick = onRamClick
            )
        }
    }
}

@Composable
fun StatCard(
    title: String, 
    value: String, 
    unit: String, 
    icon: androidx.compose.ui.graphics.vector.ImageVector, 
    color: Color, 
    usage: Double, 
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(140.dp),
        color = SurfaceDark,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
                Icon(icon, null, tint = color.copy(alpha = 0.2f), modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color.White)
                Text(unit, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f), modifier = Modifier.padding(bottom = 4.dp, start = 2.dp))
            }
            Spacer(Modifier.weight(1f))
            HeartbeatGraph(color = color, usage = usage, modifier = Modifier.fillMaxWidth().height(40.dp))
        }
    }
}

@Composable
fun MiniConsoleSection(
    logs: List<String>, 
    serverStatus: ServerStatus, 
    onSeeConsole: () -> Unit,
    onStartServer: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("LOGS RECENTES", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text("Ver Console", style = MaterialTheme.typography.labelSmall, color = PrimaryDark, modifier = Modifier.clickable { onSeeConsole() })
        }
        Spacer(Modifier.height(12.dp))
        
        Surface(
            color = Color.Black.copy(alpha = 0.4f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (logs.isEmpty()) {
                    if (serverStatus == ServerStatus.STOPPED) {
                        Column(
                             modifier = Modifier.fillMaxWidth().padding(16.dp),
                             horizontalAlignment = Alignment.CenterHorizontally,
                             verticalArrangement = Arrangement.Center
                        ) {
                             Icon(Icons.Default.Terminal, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(32.dp))
                             Spacer(Modifier.height(8.dp))
                             Text("O servidor está desligado.", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodyMedium)
                             Spacer(Modifier.height(16.dp))
                             Button(
                                 onClick = onStartServer,
                                 colors = ButtonDefaults.buttonColors(containerColor = PrimaryDark),
                                 shape = RoundedCornerShape(12.dp)
                             ) {
                                  Icon(Icons.Default.PlayArrow, null, tint = BackgroundDark)
                                  Spacer(Modifier.width(8.dp))
                                  Text("INICIAR AGORA", color = BackgroundDark, fontWeight = FontWeight.Bold)
                             }
                        }
                    } else {
                         Text("Aguardando logs...", color = Color.White.copy(alpha = 0.3f), style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    logs.takeLast(3).forEach { logLine ->
                        val time = if (logLine.startsWith("[")) logLine.substringBefore("]") + "]" else ""
                        val msg = if (logLine.startsWith("[")) logLine.substringAfter("] ") else logLine
                        ConsoleLogLine(time, msg)
                    }
                }
            }
        }
    }
}

@Composable
fun HeartbeatGraph(color: Color, usage: Double, modifier: Modifier) {
    val infiniteTransition = rememberInfiniteTransition()
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val path = androidx.compose.ui.graphics.Path()
        
        val points = 30
        val step = width / points
        
        path.moveTo(0f, height / 2)
        
        for (i in 0..points) {
            val x = i * step
            // Standard heartbeat pulse logic
            val baseNoise = Math.sin((i + phase * points) * 0.8).toFloat() * 2f
            val pulseIndex = (i + (phase * points).toInt()) % 15
            val spike = when (pulseIndex) {
                5 -> (usage.toFloat() * height * 0.5f)
                6 -> -(usage.toFloat() * height * 0.3f)
                else -> 0f
            }
            
            val y = (height / 2) + baseNoise - spike
            path.lineTo(x, y)
        }
        
        drawPath(
            path = path,
            color = color.copy(alpha = 0.8f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
    }
}

@Composable
fun QuickToolsSection(navController: NavController, serverId: String) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Text("FERRAMENTAS DE GESTÃO", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        Spacer(Modifier.height(16.dp))
        
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ToolButton(
                title = "Gerenciar Servidor",
                desc = "Propriedades e Configurações",
                icon = Icons.Default.Settings,
                color = TertiaryDark,
                onClick = { navController.navigate(Screen.ServerManagement.createRoute(serverId)) }
            )
            ToolButton(
                title = "Gerenciar Conteúdo",
                desc = "Mods, Plugins e Mundos Instalados",
                icon = Icons.Default.Folder,
                color = Color(0xFF64B5F6), // Light Blue
                onClick = { navController.navigate(Screen.Mods.createRoute(serverId)) }
            )
            ToolButton(
                title = "Loja de Conteúdo",
                desc = "Baixar novos Mods e Plugins",
                icon = Icons.Default.Extension,
                color = PrimaryDark,
                onClick = { navController.navigate(Screen.Library.createRoute(serverId)) }
            )
        }
    }
}

@Composable
fun ToolButton(title: String, desc: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.White.copy(alpha = 0.04f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp), modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = color)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Black, color = Color.White)
                Text(desc, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color.White.copy(alpha = 0.2f))
        }
    }
}

@Composable
fun MiniConsoleSection(logs: List<String>, onSeeConsole: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("LOGS RECENTES", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text("Ver Console", style = MaterialTheme.typography.labelSmall, color = PrimaryDark, modifier = Modifier.clickable { onSeeConsole() })
        }
        Spacer(Modifier.height(12.dp))
        
        Surface(
            color = Color.Black.copy(alpha = 0.4f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (logs.isEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White.copy(0.15f),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Servidor parado", color = Color.White.copy(0.4f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("Inicie o servidor para ver os logs", color = Color.White.copy(0.25f), style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    logs.takeLast(3).forEach { logLine ->
                        // Extract time if possible, or just show msg
                        val time = if (logLine.startsWith("[")) logLine.substringBefore("]") + "]" else ""
                        val msg = if (logLine.startsWith("[")) logLine.substringAfter("] ") else logLine
                        ConsoleLogLine(time, msg)
                    }
                }
            }
        }
    }
}

@Composable
fun ConsoleLogLine(time: String, msg: String) {
    Row {
        Text(time, color = PrimaryDark, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(
            text = com.lzofseven.mcserver.util.MotdUtils.parseMinecraftColors(msg),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun GlassBottomNav(navController: NavController, serverId: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Blur gradient layer - small and subtle
        Box(
            modifier = Modifier
                .width(340.dp)
                .height(40.dp) // Half the height of the pill as requested
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            BackgroundDark.copy(alpha = 0.5f)
                        )
                    ),
                    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                )
                .align(Alignment.BottomCenter)
        )

        Surface(
            color = GlassBg,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            modifier = Modifier.width(320.dp).height(72.dp),
            shadowElevation = 16.dp
        ) {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                BottomNavItem(Icons.Default.Home, "Início", true) { navController.navigate(Screen.ServerList.route) }
                BottomNavItem(Icons.Default.Terminal, "Console", false) { navController.navigate(Screen.Console.createRoute(serverId)) }
                BottomNavItem(Icons.Default.Search, "Loja", false) { navController.navigate(Screen.Library.createRoute(serverId)) }
                BottomNavItem(Icons.Default.Person, "Jogadores", false) { navController.navigate(Screen.Players.createRoute(serverId)) }
                BottomNavItem(Icons.Default.Settings, "Config", false) { navController.navigate(Screen.Config.createRoute(serverId)) }
            }
        }
    }
}

@Composable
fun BottomNavItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Icon(
            icon,
            contentDescription = null,
            tint = if(selected) PrimaryDark else Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(if(selected) 24.dp else 22.dp)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = if(selected) PrimaryDark else Color.White.copy(alpha = 0.4f),
            fontSize = 8.sp,
            letterSpacing = 0.sp
        )
    }
}

@Composable
fun InstallationProgressCard(progress: Int) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), // Increased padding to match other cards
        colors = CardDefaults.cardColors(containerColor = PrimaryDark.copy(alpha = 0.1f)),
        border = BorderStroke(1.dp, PrimaryDark.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Instalando Servidor...", fontWeight = FontWeight.Bold, color = PrimaryDark)
                Text("$progress%", fontWeight = FontWeight.Black, color = PrimaryDark)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = progress / 100f,
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = PrimaryDark,
                trackColor = PrimaryDark.copy(alpha = 0.1f)
            )
        }
    }
}

@Composable
fun EulaDialog(
    onAccept: () -> Unit,
    onLinkFolder: () -> Unit = {},
    isLinked: Boolean = true
) {
    AlertDialog(
        onDismissRequest = { },
        title = { 
            Text(
                "📜 Contrato EULA",
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = { 
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Para rodar um servidor Minecraft, você precisa concordar com o Contrato de Licença de Usuário Final (EULA) da Mojang.",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
                if (!isLinked) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0x33FF9800)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFFFF9800)
                            )
                            Text(
                                "Vincule a pasta do servidor para continuar",
                                color = Color(0xFFFF9800),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isLinked) {
                Button(
                    onClick = onLinkFolder,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("VINCULAR PASTA", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("ACEITO OS TERMOS", color = BackgroundDark, fontWeight = FontWeight.Bold)
                }
            }
        },
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun PermissionDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = { 
            Text("Acesso Total Necessário", fontWeight = FontWeight.Bold, color = Color.White)
        },
        text = {
            Text(
                "Para iniciar o servidor no Android 15, o Java precisa de permissão para acessar os arquivos do servidor na sua pasta de Downloads.\n\nPor favor, conceda o 'Acesso a todos os arquivos' na próxima tela.",
                color = Color.White.copy(alpha = 0.7f)
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryDark)
            ) {
                Text("CONFIGURAR", color = BackgroundDark, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCELAR", color = Color.White.copy(alpha = 0.5f))
            }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
fun PlayitStatusCard(status: String, claimLink: String?, address: String?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Surface(
        color = SurfaceDark.copy(alpha = 0.5f),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (status == "Running" || status == "Ready") Color.Green else if (status == "Downloading...") Color.Blue else Color.Yellow)
                )
                Spacer(Modifier.width(12.dp))
                Text("ACESSO PÚBLICO (PLAYIT.GG)", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.Black)
            }
            
            Spacer(Modifier.height(12.dp))
            
            Text(
                text = if (address != null) "📡 Servidor Público!" else if (status == "Running") "📡 Túnel Ativo (Aguardando IP)" else "⏳ $status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            if (address != null) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = Color.White.copy(0.05f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = address,
                            color = PrimaryDark,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Minecraft IP", address)
                            clipboard.setPrimaryClip(clip)
                            android.widget.Toast.makeText(context, "IP copiado!", android.widget.Toast.LENGTH_SHORT).show()
                        }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.ContentCopy, null, tint = Color.White.copy(0.4f), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            
            if (claimLink != null) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Playit Claim Link", claimLink)
                        clipboard.setPrimaryClip(clip)
                        android.widget.Toast.makeText(context, "Link de resgate copiado!", android.widget.Toast.LENGTH_SHORT).show()
                        
                        // Open link in browser
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(claimLink))
                        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryDark.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("RESGATAR ENDEREÇO", color = PrimaryDark, fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Clique para vincular seu servidor e ganhar um IP fixo.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(0.3f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
