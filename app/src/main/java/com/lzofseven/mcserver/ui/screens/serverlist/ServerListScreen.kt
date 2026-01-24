package com.lzofseven.mcserver.ui.screens.serverlist

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lzofseven.mcserver.data.local.entity.MCServerEntity
import com.lzofseven.mcserver.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(
    navController: NavController,
    viewModel: ServerListViewModel = hiltViewModel()
) {
    val servers by viewModel.servers.collectAsState()
    val onlineCount by viewModel.onlineCount.collectAsState()
    val motds by viewModel.motds.collectAsState()
    val icons by viewModel.icons.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(BackgroundDarkV2)) {
        // Pixel Background Effect (Static Cached Layer)
        val gridColor = Color.White.copy(alpha = 0.02f)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer()
        ) {
            val gridSize = 20.dp.toPx()
            for (x in 0..size.width.toInt() step gridSize.toInt()) {
                drawLine(
                    color = gridColor,
                    start = Offset(x.toFloat(), 0f),
                    end = Offset(x.toFloat(), size.height),
                    strokeWidth = 1f
                )
            }
            for (y in 0..size.height.toInt() step gridSize.toInt()) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y.toFloat()),
                    end = Offset(size.width, y.toFloat()),
                    strokeWidth = 1f
                )
            }
        }

        // Gradient Glow Top
        Box(modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
            .background(Brush.verticalGradient(listOf(EmeraldPrimary.copy(alpha = 0.05f), Color.Transparent)))
        )

        Scaffold(
            containerColor = Color.Transparent,
            modifier = Modifier.statusBarsPadding()
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp)
            ) {
                // Hero Section V2
                HeroBannerV2(servers, onlineCount)
                
                Spacer(modifier = Modifier.height(32.dp))

                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), 
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier
                            .size(width = 8.dp, height = 24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Brush.verticalGradient(listOf(EmeraldPrimary, Color(0xFF059669))))
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Meus Servidores",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Text(
                        "Gerenciar",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(servers) { server ->
                        ServerItemCardV2(
                            server = server,
                            motd = motds[server.id] ?: "Carregando...",
                            icon = icons[server.id],
                            isOnline = viewModel.isServerRunning(server.id)
                        ) {
                            navController.navigate("dashboard/${server.id}")
                        }
                    }

                    item {
                        CreateServerDashedCard(onClick = { navController.navigate("create_server") })
                    }
                }
            }
        }
    }
}

@Composable
fun HeroBannerV2(servers: List<MCServerEntity>, onlineCount: Int) {
    val serverName = if (servers.isNotEmpty()) servers.first().name else "MC Server Manager"
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .padding(top = 16.dp),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = "https://lh3.googleusercontent.com/aida-public/AB6AXuB4rbRqvymf-5EzL5vl_mmGY26E31KuBnLLPc9_qQdYbCGT4nEgctmGS0EZRK-8pVCDLfuNENZyjyxkZm4hOQeakusjKGRIddEF5jIBiv3f8_n9A4317Sz5yuG0MBRo5yIgMm1kBY07g4BNPz0AeHKKci97B9vKYNUcuY-jtf6K6PVsDBl5NPS4swyeEzPusKtKktlKHa2XnN0UGP6lgXmRZvXZIO8l2ogafn9Bl3WP81K8nZrUVyiqAlsfBcNqGTnWZli3nOdsX7JC",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(
                listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
            )))
            
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusBadgeV2("$onlineCount ONLINE", isOnline = onlineCount > 0)
                    StatusBadgeV2("${servers.size} TOTAL", isOnline = false, icon = Icons.Rounded.Dns)
                }
                Spacer(Modifier.height(12.dp))
                Text("Central de Servidores", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text("Gerencie seu universo Minecraft em tempo real", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
fun StatusBadgeV2(text: String, isOnline: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    val infiniteTransition = rememberInfiniteTransition()
    val pingPadding by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart)
    )
    val pingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart)
    )

    Surface(
        color = Color.Black.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if(isOnline) EmeraldPrimary.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f)),
        modifier = Modifier.height(32.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isOnline) {
                Box(contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(14.dp).drawBehind {
                        drawCircle(color = EmeraldPrimary.copy(alpha = pingAlpha), radius = (6.dp.toPx() + pingPadding.dp.toPx()))
                    })
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(EmeraldPrimary))
                }
            } else if (icon != null) {
                Icon(icon, null, tint = Color.LightGray, modifier = Modifier.size(14.dp))
            }
            Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if(isOnline) EmeraldPrimary else Color.White)
        }
    }
}

@Composable
fun ServerItemCardV2(server: MCServerEntity, motd: String, icon: android.graphics.Bitmap?, isOnline: Boolean, onClick: () -> Unit) {

    Box(modifier = Modifier
        .fillMaxWidth()
        .clickable { onClick() }
        .clip(RoundedCornerShape(24.dp))
    ) {
        // Glass Effect Background
        Box(modifier = Modifier
            .matchParentSize()
            .background(Color(0xFF161B22).copy(alpha = 0.6f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
        )
        
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(64.dp)) {
                Surface(
                    color = Color(0xFF2a3038),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (icon != null) {
                            androidx.compose.foundation.Image(
                                bitmap = icon.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                server.name.take(1).uppercase(),
                                color = if (isOnline) EmeraldPrimary else Color.Gray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp
                            )
                        }
                    }
                }
                if (isOnline) {
                    Box(modifier = Modifier.size(16.dp).background(EmeraldPrimary, CircleShape).border(3.dp, Color(0xFF161B22), CircleShape).align(Alignment.BottomEnd))
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(server.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Surface(
                        color = if(isOnline) EmeraldPrimary.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if(isOnline) EmeraldPrimary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f))
                    ) {
                        Text(
                            if(isOnline) "ONLINE" else "OFFLINE",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if(isOnline) EmeraldPrimary else Color.Gray
                        )
                    }
                }

                Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ServerChipV2("${server.type} ${server.version}", Color.Transparent)
                    ServerChipV2("${server.ramAllocationMB / 1024}GB", Color.Transparent, Icons.Rounded.Memory)
                }
                
                val annotatedMotd = remember(motd) {
                    com.lzofseven.mcserver.util.MotdUtils.parseMinecraftColors(motd)
                }
                Text(
                    text = annotatedMotd,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(alpha = 0.2f))
        }
    }
}

@Composable
fun ServerChipV2(text: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Surface(
        color = Color.Black.copy(alpha = 0.2f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (icon != null) Icon(icon, null, tint = Color.Gray, modifier = Modifier.size(10.dp))
            else Box(modifier = Modifier.size(6.dp).background(EmeraldPrimary, CircleShape))
            Text(text, style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
        }
    }
}

@Composable
fun CreateServerDashedCard(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clickable { onClick() }
            .drawBehind {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.1f),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx())
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.AddCircleOutline, null, tint = Color.Gray, modifier = Modifier.size(24.dp))
            Text("CRIAR NOVO SERVIDOR", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.Gray)
        }
    }
}
