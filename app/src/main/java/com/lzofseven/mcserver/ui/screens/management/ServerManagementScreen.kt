package com.lzofseven.mcserver.ui.screens.management

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import com.lzofseven.mcserver.util.MotdUtils
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.lzofseven.mcserver.ui.theme.BackgroundDark
import com.lzofseven.mcserver.ui.theme.PrimaryDark
import com.lzofseven.mcserver.ui.theme.SurfaceDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerManagementScreen(
    navController: NavController,
    viewModel: ServerManagementViewModel = hiltViewModel()
) {
    val motd by viewModel.motd.collectAsState()
    var motdTextFieldValue by remember(motd) { 
        mutableStateOf(TextFieldValue(motd, TextRange(motd.length))) 
    }
    
    val maxPlayers by viewModel.maxPlayers.collectAsState()
    val onlineMode by viewModel.onlineMode.collectAsState()
    val gameMode by viewModel.gameMode.collectAsState()
    val whiteList by viewModel.whiteList.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(key1 = true) {
        viewModel.uiEvent.collect { event ->
            when(event) {
                is com.lzofseven.mcserver.ui.screens.config.UiEvent.ShowToast -> {
                    android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val serverPort by viewModel.serverPort.collectAsState()
    val serverIp by viewModel.serverIp.collectAsState()
    val enableQuery by viewModel.enableQuery.collectAsState()
    val enableRcon by viewModel.enableRcon.collectAsState()
    val rconPassword by viewModel.rconPassword.collectAsState()

    val levelName by viewModel.levelName.collectAsState()
    val levelSeed by viewModel.levelSeed.collectAsState()
    val levelType by viewModel.levelType.collectAsState()
    val pvp by viewModel.pvp.collectAsState()
    val difficulty by viewModel.difficulty.collectAsState()
    val hardcore by viewModel.hardcore.collectAsState()
    val allowNether by viewModel.allowNether.collectAsState()

    val generateStructures by viewModel.generateStructures.collectAsState()
    val allowFlight by viewModel.allowFlight.collectAsState()
    val spawnAnimals by viewModel.spawnAnimals.collectAsState()
    val spawnNpcs by viewModel.spawnNpcs.collectAsState()

    val viewDistance by viewModel.viewDistance.collectAsState()
    val simulationDistance by viewModel.simulationDistance.collectAsState()

    val javaVersion by viewModel.javaVersion.collectAsState()
    val autoStart by viewModel.autoStart.collectAsState()

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Gerenciar Servidor", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color.White)
                        Text("Configurações do Mundo", style = MaterialTheme.typography.labelSmall, color = PrimaryDark)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Voltar", tint = Color.White)
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
            // Server Icon Section
            ManagementSection(title = "ÍCONE DO SERVIDOR", icon = Icons.Default.Public) {
                val iconPath by viewModel.serverIconPath.collectAsState()
                val iconUpdate by viewModel.serverIconUpdate.collectAsState()
                var iconBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                
                val context = androidx.compose.ui.platform.LocalContext.current
                LaunchedEffect(iconPath, iconUpdate) {
                    if (iconPath != null) {
                        try {
                            if (iconPath!!.startsWith("content://")) {
                                val uri = android.net.Uri.parse(iconPath!!)
                                context.contentResolver.openInputStream(uri)?.use { stream ->
                                    iconBitmap = android.graphics.BitmapFactory.decodeStream(stream)
                                }
                            } else {
                                val file = java.io.File(iconPath!!)
                                if (file.exists()) {
                                    iconBitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                                }
                            }
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                }

                val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
                ) { uri: android.net.Uri? ->
                    uri?.let { viewModel.setServerIcon(it) }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color.Black, RoundedCornerShape(4.dp))
                            .clickable { launcher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (iconBitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = iconBitmap!!.asImageBitmap(),
                                contentDescription = "Server Icon",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text("64x64", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    
                    Spacer(Modifier.width(16.dp))
                    
                    Column {
                        Button(
                            onClick = { launcher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Selecionar Imagem", color = Color.White)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "A imagem será redimensionada para 64x64px.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            // Identity and Network Section
            ManagementSection(title = "IDENTIFICAÇÃO E REDE", icon = Icons.Default.Dns) {
                // MOTD Creator Toolbar
                MotdToolbar(
                    onCodeClick = { code ->
                        val text = motdTextFieldValue.text
                        val selection = motdTextFieldValue.selection
                        val newText = text.substring(0, selection.start) + code + text.substring(selection.end)
                        val newSelection = TextRange(selection.start + code.length)
                        motdTextFieldValue = TextFieldValue(newText, newSelection)
                        viewModel.setMotd(newText)
                    }
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = motdTextFieldValue,
                    onValueChange = { 
                        motdTextFieldValue = it
                        viewModel.setMotd(it.text)
                    },
                    label = { Text("Descrição (MOTD)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = managementTextFieldColors()
                )

                Spacer(Modifier.height(12.dp))

                // MOTD Live Preview
                Text("Pré-visualização ao vivo:", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black, RoundedCornerShape(8.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = MotdUtils.parseMinecraftColors(motd),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                Spacer(Modifier.height(12.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = serverPort,
                        onValueChange = { if(it.all { char -> char.isDigit() }) viewModel.setServerPort(it) },
                        label = { Text("Porta TCP") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = managementTextFieldColors(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = serverIp,
                        onValueChange = { viewModel.setServerIp(it) },
                        label = { Text("IP (Opcional)") },
                        placeholder = { Text("Ex: 127.0.0.1") },
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(16.dp),
                        colors = managementTextFieldColors(),
                        singleLine = true
                    )
                }

                Spacer(Modifier.height(12.dp))

                ManagementToggle(
                    title = "Modo Pirata (Cracked)",
                    desc = "Permitir jogadores sem conta oficial",
                    checked = !onlineMode,
                    onCheckedChange = { viewModel.setOnlineMode(!it) }
                )

                Spacer(Modifier.height(12.dp))

                ManagementToggle(
                    title = "Whitelist (Lista Branca)",
                    desc = "Permitir apenas jogadores na lista",
                    checked = whiteList,
                    onCheckedChange = { viewModel.setWhiteList(it) }
                )

                Spacer(Modifier.height(12.dp))

                ManagementToggle(
                    title = "Protocolo Query",
                    desc = "Ativa estatísticas via Query - Reinício necessário",
                    checked = enableQuery,
                    onCheckedChange = { viewModel.setEnableQuery(it) }
                )

                if (enableQuery) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("💡", fontSize = 18.sp)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "O Query mostra quem está online e o mapa, mas NÃO fornece coordenadas exatas. Para ver a localização, use RCON ou plugins.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                ManagementToggle(
                    title = "Acesso RCON",
                    desc = "Permitir comando remoto",
                    checked = enableRcon,
                    onCheckedChange = { viewModel.setEnableRcon(it) }
                )

                if (enableRcon) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = rconPassword,
                        onValueChange = { viewModel.setRconPassword(it) },
                        label = { Text("Senha RCON") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = managementTextFieldColors(),
                        singleLine = true
                    )
                }
            }

            // World Section
            ManagementSection(title = "MUNDO E GERAÇÃO", icon = Icons.Default.Public) {
                OutlinedTextField(
                    value = levelName,
                    onValueChange = { viewModel.setLevelName(it) },
                    label = { Text("Nome da Pasta") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = managementTextFieldColors(),
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = levelSeed,
                    onValueChange = { viewModel.setLevelSeed(it) },
                    label = { Text("Seed do Mundo") },
                    placeholder = { Text("Vazio = Aleatório") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = managementTextFieldColors(),
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))

                ManagementSection(title = "TIPO DE MUNDO", icon = Icons.Default.Language) {
                    val types = listOf(
                        "default" to "Padrão",
                        "flat" to "Plano",
                        "largeBiomes" to "Biomas Grandes",
                        "amplified" to "Amplificado"
                    )
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        types.forEach { (value, label) ->
                            FilterChip(
                                selected = levelType == value,
                                onClick = { viewModel.setLevelType(value) },
                                label = { Text(label) },
                                colors = managementChipColors()
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                ManagementToggle(
                    title = "Gerar Estruturas",
                    desc = "Vilas, Fortalezas, etc.",
                    checked = generateStructures,
                    onCheckedChange = { viewModel.setGenerateStructures(it) }
                )

                Spacer(Modifier.height(12.dp))

                ManagementToggle(
                    title = "Acesso ao Nether",
                    desc = "Permite dimensões do Nether",
                    checked = allowNether,
                    onCheckedChange = { viewModel.setAllowNether(it) }
                )
            }

            // Gameplay Section
            ManagementSection(title = "JOGABILIDADE", icon = Icons.Default.SportsEsports) {
                ManagementSection(title = "MODO DE JOGO PADRÃO", icon = null) {
                    val modes = listOf(
                        "survival" to "Sobrevivência",
                        "creative" to "Criativo",
                        "adventure" to "Aventura",
                        "spectator" to "Espectador"
                    )
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        modes.forEach { (value, label) ->
                            FilterChip(
                                selected = gameMode == value,
                                onClick = { viewModel.setGameMode(value) },
                                label = { Text(label) },
                                colors = managementChipColors()
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                ManagementSection(title = "DIFICULDADE", icon = null) {
                    val diffs = listOf(
                        "peaceful" to "Pacífico",
                        "easy" to "Fácil",
                        "normal" to "Normal",
                        "hard" to "Difícil"
                    )
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        diffs.forEach { (value, label) ->
                            FilterChip(
                                selected = difficulty == value,
                                onClick = { viewModel.setDifficulty(value) },
                                label = { Text(label) },
                                colors = managementChipColors()
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = maxPlayers,
                        onValueChange = { if(it.all { char -> char.isDigit() }) viewModel.setMaxPlayers(it) },
                        label = { Text("Máx. Players") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = managementTextFieldColors(),
                        singleLine = true
                    )
                }

                Spacer(Modifier.height(12.dp))

                ManagementToggle(
                    title = "PVP Habilitado",
                    desc = "Permite combate entre jogadores",
                    checked = pvp,
                    onCheckedChange = { viewModel.setPvp(it) }
                )

                Spacer(Modifier.height(12.dp))

                ManagementToggle(
                    title = "Modo Hardcore",
                    desc = "Morte permanente",
                    checked = hardcore,
                    onCheckedChange = { viewModel.setHardcore(it) }
                )

                Spacer(Modifier.height(12.dp))

                ManagementToggle(
                    title = "Permitir Voo",
                    desc = "Habilita voar no modo sobrevivência",
                    checked = allowFlight,
                    onCheckedChange = { viewModel.setAllowFlight(it) }
                )

                Spacer(Modifier.height(12.dp))

                ManagementToggle(
                    title = "Gerar Animais",
                    desc = "Spawn de animais pacíficos",
                    checked = spawnAnimals,
                    onCheckedChange = { viewModel.setSpawnAnimals(it) }
                )

                Spacer(Modifier.height(12.dp))

                ManagementToggle(
                    title = "Gerar NPCs",
                    desc = "Spawn de aldeões e monstros",
                    checked = spawnNpcs,
                    onCheckedChange = { viewModel.setSpawnNpcs(it) }
                )
            }

            // Performance Section
            ManagementSection(title = "PERFORMANCE", icon = Icons.Default.BarChart) {
                 Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = viewDistance,
                        onValueChange = { if(it.all { char -> char.isDigit() }) viewModel.setViewDistance(it) },
                        label = { Text("Visão (Chunks)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = managementTextFieldColors(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = simulationDistance,
                        onValueChange = { if(it.all { char -> char.isDigit() }) viewModel.setSimulationDistance(it) },
                        label = { Text("Simulação") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = managementTextFieldColors(),
                        singleLine = true
                    )
                }
            }

            // System Section
            ManagementSection(title = "SISTEMA", icon = Icons.Default.Terminal) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Java Version Selector
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Versão do Java", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                            Spacer(Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(11, 17, 21).forEach { ver ->
                                    FilterChip(
                                        selected = javaVersion == ver,
                                        onClick = { viewModel.setJavaVersion(ver) },
                                        label = { Text("Java $ver") },
                                        colors = managementChipColors()
                                    )
                                }
                            }
                        }
                    }

                    // AutoStart Toggle
                    ManagementToggle(
                        title = "Início Automático",
                        desc = "Iniciar este servidor assim que o app abrir",
                        checked = autoStart,
                        onCheckedChange = { viewModel.setAutoStart(it) }
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = { 
                    viewModel.saveProperties()
                    navController.popBackStack()
                },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryDark),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Save, null, tint = BackgroundDark)
                Spacer(Modifier.width(8.dp))
                Text("SALVAR ALTERAÇÕES", fontWeight = FontWeight.Black, color = BackgroundDark)
            }
        }
    }
}

@Composable
fun ManagementSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, content: @Composable () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(title, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
fun ManagementToggle(title: String, desc: String = "", checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(
        color = SurfaceDark.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = Color.White)
                Text(desc, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedTrackColor = PrimaryDark)
            )
        }
    }
}

@Composable
fun MotdToolbar(onCodeClick: (String) -> Unit) {
    val colors = listOf(
        "&0" to Color(0xFF000000), "&1" to Color(0xFF0000AA), "&2" to Color(0xFF00AA00), "&3" to Color(0xFF00AAAA),
        "&4" to Color(0xFFAA0000), "&5" to Color(0xFFAA00AA), "&6" to Color(0xFFFFAA00), "&7" to Color(0xFFAAAAAA),
        "&8" to Color(0xFF555555), "&9" to Color(0xFF5555FF), "&a" to Color(0xFF55FF55), "&b" to Color(0xFF55FFFF),
        "&c" to Color(0xFFFF5555), "&d" to Color(0xFFFF55FF), "&e" to Color(0xFFFFFF55), "&f" to Color(0xFFFFFFFF)
    )
    val formats = listOf(
        "&l" to "B", "&o" to "I", "&n" to "U", "&m" to "S", "&r" to "R"
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            colors.forEach { (code, color) ->
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(color, RoundedCornerShape(4.dp))
                        .clickable { onCodeClick(code) }
                )
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            formats.forEach { (code, label) ->
                Surface(
                    onClick = { onCodeClick(code) },
                    shape = RoundedCornerShape(4.dp),
                    color = SurfaceDark,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun managementChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = PrimaryDark,
    selectedLabelColor = BackgroundDark,
    containerColor = SurfaceDark,
    labelColor = Color.White
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun managementTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PrimaryDark,
    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedLabelColor = PrimaryDark,
    unfocusedLabelColor = Color.White.copy(alpha = 0.4f)
)
