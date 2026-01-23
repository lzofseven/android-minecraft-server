package com.lzofseven.mcserver.ui.screens.config

import androidx.compose.foundation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.lzofseven.mcserver.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConfigScreen(
    navController: NavController,
    viewModel: ConfigViewModel = hiltViewModel()
) {
    val serverType by viewModel.serverType.collectAsState()
    val mcVersion by viewModel.mcVersion.collectAsState()
    val ramAllocation by viewModel.ramAllocation.collectAsState()
    val autoAcceptEula by viewModel.autoAcceptEula.collectAsState()
    val cpuCores by viewModel.cpuCores.collectAsState()
    val forceMaxFrequency by viewModel.forceMaxFrequency.collectAsState()
    val worldPath by viewModel.worldPath.collectAsState()
    
    var showRestartModal by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(key1 = true) {
        viewModel.uiEvent.collect { event: UiEvent ->
            when(event) {
                is UiEvent.ShowToast -> {
                     android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    LaunchedEffect(key1 = true) {
        viewModel.restartRequiredEvent.collect { 
            showRestartModal = true
        }
    }
    
    val isSaving by viewModel.isSaving.collectAsState()
    
    LaunchedEffect(key1 = true) {
        viewModel.saveSuccessEvent.collect {
            navController.popBackStack()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { 
                        Column {
                            Text("Ajustes do Sistema", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color.White)
                            Text("Otimização & Performance", style = MaterialTheme.typography.labelSmall, color = PrimaryDark, fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Voltar", tint = Color.White.copy(alpha = 0.6f))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Server Engine Section
                ConfigSection(
                    title = "MOTOR DO SERVIDOR", 
                    icon = Icons.Default.Settings,
                    subtitle = "Reinício necessário"
                ) {
                    var expanded by remember { mutableStateOf(false) }
                    
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = serverType.displayName,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                leadingIcon = {
                                    val icon = when (serverType) {
                                        ServerType.PAPER -> Icons.Default.Speed
                                        ServerType.FABRIC -> Icons.Default.Layers
                                        ServerType.VANILLA -> Icons.Default.Grass
                                        ServerType.FORGE -> Icons.Default.Construction
                                        ServerType.NEOFORGE -> Icons.Default.AutoAwesome
                                        ServerType.BUKKIT -> Icons.Default.Inventory
                                        ServerType.SPIGOT -> Icons.Default.Bolt
                                    }
                                    Icon(icon, null, tint = PrimaryDark)
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryDark,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                                    focusedContainerColor = Color.White.copy(alpha = 0.05f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            // Click overlay
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { expanded = !expanded }
                            )
                        }
                        
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier
                                .exposedDropdownSize(matchTextFieldWidth = true)
                                .heightIn(max = 250.dp)
                                .background(SurfaceDark)
                                .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(4.dp))
                        ) {
                            ServerType.values().forEach { type ->
                                val icon = when (type) {
                                    ServerType.PAPER -> Icons.Default.Speed
                                    ServerType.FABRIC -> Icons.Default.Layers
                                    ServerType.VANILLA -> Icons.Default.Grass
                                    ServerType.FORGE -> Icons.Default.Construction
                                    ServerType.NEOFORGE -> Icons.Default.AutoAwesome
                                    ServerType.BUKKIT -> Icons.Default.Inventory
                                    ServerType.SPIGOT -> Icons.Default.Bolt
                                }
                                
                                DropdownMenuItem(
                                    text = { 
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(type.displayName, color = Color.White, fontWeight = FontWeight.SemiBold)
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(icon, null, tint = if(serverType == type) PrimaryDark else Color.White.copy(0.7f))
                                    },
                                    onClick = {
                                        viewModel.selectServerType(type)
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                /* Versão removida para evitar conflito lógico com a criação */

                // Resource Allocation Section
                ConfigSection(
                    title = "ALOCAÇÃO DE RECURSOS", 
                    icon = Icons.Default.Memory,
                    subtitle = "Reinício necessário"
                ) {
                    Surface(
                        color = Color.White.copy(alpha = 0.03f),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            // RAM
                            ResourceSlider(
                                label = "Memória RAM",
                                value = "${ramAllocation}MB",
                                icon = Icons.Default.Storage,
                                progress = (ramAllocation - 512f) / (4096f - 512f),
                                onValueChange = { viewModel.setRamAllocation((512 + it * (4096 - 512)).toInt()) }
                            )
                            
                            val ramInfo by viewModel.ramInfo.collectAsState()
                            ramInfo?.let { 
                                Text(
                                    text = "Disp: ${String.format("%.1f", it.availableRamGB)}GB / Total: ${String.format("%.1f", it.totalRamGB)}GB",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(0.4f),
                                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                                )
                            }
                            
                            Spacer(Modifier.height(24.dp))
                            
                            // CPU
                            val maxCores = Runtime.getRuntime().availableProcessors()
                            ResourceSlider(
                                label = "Núcleos de CPU",
                                value = "$cpuCores Cores",
                                icon = Icons.Default.Memory,
                                progress = (cpuCores - 1f) / (maxCores - 1f).coerceAtLeast(1f),
                                onValueChange = { 
                                    val newCores = (1 + it * (maxCores - 1)).toInt().coerceIn(1, maxCores)
                                    viewModel.setCpuCores(newCores) 
                                }
                            )

                            Spacer(Modifier.height(24.dp))

                            // Java Version
                            val javaVersion by viewModel.javaVersion.collectAsState()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Terminal, null, tint = PrimaryDark, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Versão do Java", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                                }
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FilterChip(
                                        selected = javaVersion == 17,
                                        onClick = { viewModel.setJavaVersion(17) },
                                        label = { Text("Java 17") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = PrimaryDark,
                                            selectedLabelColor = BackgroundDark,
                                            labelColor = Color.White.copy(0.6f)
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = javaVersion == 17,
                                            borderColor = Color.White.copy(0.1f),
                                            selectedBorderColor = PrimaryDark
                                        )
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    FilterChip(
                                        selected = javaVersion == 21,
                                        onClick = { viewModel.setJavaVersion(21) },
                                        label = { Text("Java 21") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = PrimaryDark,
                                            selectedLabelColor = BackgroundDark,
                                            labelColor = Color.White.copy(0.6f)
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = javaVersion == 21,
                                            borderColor = Color.White.copy(0.1f),
                                            selectedBorderColor = PrimaryDark
                                        )
                                    )
                                }
                            }
                            Text(
                                "Nota: Java 21 é recomendado para plugins modernos (1.20.1+).",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(0.4f),
                                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                            )
                        }
                    }
                }

                // Performance Toggles
                ConfigSection(title = "OTIMIZAÇÃO", icon = Icons.Default.Bolt) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PremiumSwitch(
                            title = "Frequência Máxima CPU",
                            desc = "Aumenta desempenho (Gamer Mode)",
                            checked = forceMaxFrequency,
                            onCheckedChange = { viewModel.setForceMaxFrequency(it) }
                        )
                        PremiumSwitch(
                            title = "Auto Aceitar EULA",
                            desc = "Inicia o servidor sem confirmação",
                            checked = autoAcceptEula,
                            onCheckedChange = { viewModel.setAutoAcceptEula(it) }
                        )
                    }
                }

                // Path Config
                ConfigSection(title = "ARMAZENAMENTO", icon = Icons.Default.SdStorage) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
                    ) { uri ->
                        uri?.let {
                             val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                             context.contentResolver.takePersistableUriPermission(it, flags)
                             viewModel.setWorldPath(it.toString())
                        }
                    }
                    
                    val displayPath = remember(worldPath) {
                        try {
                            if (worldPath.startsWith("content://")) {
                                val uri = android.net.Uri.parse(worldPath)
                                val lastSegment = uri.lastPathSegment ?: "Unknown"
                                lastSegment.replace(":", " > ").replace("primary", "Armazenamento Interno") // Simple cleanup
                            } else {
                                worldPath
                            }
                        } catch(e: Exception) { worldPath }
                    }

                    OutlinedTextField(
                        value = displayPath,
                        onValueChange = { },
                        label = { Text("Caminho do Mundo", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        readOnly = true, // Force use picker
                        trailingIcon = {
                             IconButton(onClick = { launcher.launch(null) }) {
                                 Icon(Icons.Default.FolderOpen, null, tint = PrimaryDark)
                             }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryDark,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White.copy(alpha = 0.7f)
                        )
                    )
                }

                // Notificações Section
                ConfigSection(title = "NOTIFICAÇÕES", icon = Icons.Default.Notifications) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val notifyStatus by viewModel.notifyStatus.collectAsState()
                        val notifyPlayers by viewModel.notifyPlayers.collectAsState()
                        val notifyPerformance by viewModel.notifyPerformance.collectAsState()
                        val cpuThreshold by viewModel.cpuThreshold.collectAsState()
                        val ramThreshold by viewModel.ramThreshold.collectAsState()

                        PremiumSwitch(
                            title = "Status do Servidor",
                            desc = "Notificar quando ligar/desligar",
                            checked = notifyStatus,
                            onCheckedChange = { viewModel.setNotifyStatus(it) }
                        )
                        PremiumSwitch(
                            title = "Eventos de Jogadores",
                            desc = "Avisar quando alguém entrar/sair",
                            checked = notifyPlayers,
                            onCheckedChange = { viewModel.setNotifyPlayers(it) }
                        )
                        PremiumSwitch(
                            title = "Alertas de Hardware",
                            desc = "Avisar uso crítico de CPU/RAM",
                            checked = notifyPerformance,
                            onCheckedChange = { viewModel.setNotifyPerformance(it) }
                        )

                        if (notifyPerformance) {
                            Surface(
                                color = Color.White.copy(alpha = 0.03f),
                                shape = RoundedCornerShape(24.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    ResourceSlider(
                                        label = "Limite de CPU",
                                        value = "$cpuThreshold%",
                                        icon = Icons.Default.Speed,
                                        progress = cpuThreshold / 100f,
                                        onValueChange = { viewModel.setCpuThreshold((it * 100).toInt()) }
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    ResourceSlider(
                                        label = "Limite de RAM",
                                        value = "$ramThreshold%",
                                        icon = Icons.Default.SdStorage,
                                        progress = ramThreshold / 100f,
                                        onValueChange = { viewModel.setRamThreshold((it * 100).toInt()) }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                Button(
                    onClick = { 
                        viewModel.saveConfig()
                    },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryDark),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(color = BackgroundDark, modifier = Modifier.size(24.dp))
                    } else {
                        Text("SALVAR E APLICAR", fontWeight = FontWeight.Black, color = BackgroundDark, letterSpacing = 2.sp)
                    }
                }
                
                // Delete Button
                OutlinedButton(
                    onClick = {
                        // Logic to delete server (requires ViewModel implementation)
                        viewModel.deleteServer(navController)
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorDark),
                    border = BorderStroke(1.dp, ErrorDark.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.DeleteForever, null)
                    Spacer(Modifier.width(8.dp))
                    Text("DELETAR SERVIDOR", fontWeight = FontWeight.Bold)
                }
                
                Spacer(Modifier.height(48.dp))
            }
        }

        if (showRestartModal) {
            AlertDialog(
                onDismissRequest = { showRestartModal = false },
                containerColor = SurfaceDark,
                titleContentColor = Color.White,
                textContentColor = Color.White.copy(alpha = 0.7f),
                title = { Text("Reinício Necessário", fontWeight = FontWeight.Bold) },
                text = { Text("As alterações no motor ou hardware exigem que o servidor seja reiniciado para entrar em vigor. Reiniciar agora?") },
                confirmButton = {
                    Button(
                        onClick = { 
                            showRestartModal = false 
                            // TODO: Trigger actual server restart if reachable
                            android.widget.Toast.makeText(context, "O servidor será reiniciado na próxima inicialização.", android.widget.Toast.LENGTH_LONG).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryDark)
                    ) {
                        Text("Reiniciar Agora", color = BackgroundDark, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRestartModal = false }) {
                        Text("Depois", color = Color.White)
                    }
                }
            )
        }
    }
}

@Composable
fun ConfigSection(
    title: String, 
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null, 
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically, 
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (icon != null) {
                    Icon(icon, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.4f),
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            }
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = PrimaryDark.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic
                )
            }
        }
        content()
    }
}

@Composable
fun ResourceSlider(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, progress: Float, onValueChange: (Float) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = PrimaryDark, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
            }
            Text(value, fontWeight = FontWeight.Black, color = PrimaryDark, fontSize = 14.sp)
        }
        Slider(
            value = progress,
            onValueChange = onValueChange,
            colors = SliderDefaults.colors(
                thumbColor = PrimaryDark,
                activeTrackColor = PrimaryDark,
                inactiveTrackColor = Color.White.copy(alpha = 0.1f)
            ),
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun PremiumSwitch(title: String, desc: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Black, color = Color.White, fontSize = 14.sp)
                Text(desc, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BackgroundDark,
                    checkedTrackColor = PrimaryDark,
                    uncheckedThumbColor = Color.White.copy(alpha = 0.4f),
                    uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                )
            )
        }
    }
}
