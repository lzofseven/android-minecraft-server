package com.lzofseven.mcserver.ui.screens.createserver

import android.content.Intent
import android.net.Uri
import android.content.Context
import java.io.File
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.lzofseven.mcserver.ui.screens.config.ServerType
import com.lzofseven.mcserver.ui.theme.BackgroundDark
import com.lzofseven.mcserver.ui.theme.PrimaryDark
import com.lzofseven.mcserver.ui.theme.SurfaceDark

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class, ExperimentalLayoutApi::class)
@Composable
fun CreateServerScreen(
    navController: NavController,
    viewModel: CreateServerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var selectedUri by remember { mutableStateOf<String?>(null) }
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = object : ActivityResultContracts.OpenDocumentTree() {
            override fun createIntent(context: Context, input: Uri?): Intent {
                val intent = super.createIntent(context, input)
                if (input != null) {
                    intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, input)
                }
                return intent
            }
        }
    ) { uri: Uri? ->
        uri?.let {
            // Persist permission (SAF)
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                selectedUri = it.toString()
                
                // Auto-link: Update path to show user-friendly path
                viewModel.updatePath(it.toString())
                
                android.util.Log.i("CreateServerScreen", "Folder auto-linked: $it")
            } catch (e: Exception) {
                android.util.Log.e("CreateServerScreen", "Failed to persist permissions", e)
            }
        }
    }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = { Text("Novo Servidor", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Voltar", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (uiState.currentStep > 0) {
                    OutlinedButton(
                        onClick = { viewModel.previousStep() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("Voltar")
                    }
                } else {
                    Spacer(Modifier.width(10.dp))
                }

                Button(
                    onClick = {
                        if (uiState.currentStep < 3) {
                            viewModel.nextStep()
                        } else {
                            viewModel.createServer(context, selectedUri) {
                                navController.popBackStack()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryDark),
                    enabled = (uiState.currentStep != 0 || uiState.name.isNotBlank()) &&
                              (uiState.currentStep != 3 || selectedUri != null)
                ) {
                    Text(if (uiState.currentStep == 3) "Criar Servidor" else "Próximo")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(horizontal = 24.dp)) {
            // Progress Indicator
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .background(
                                if (index <= uiState.currentStep) PrimaryDark else Color.White.copy(alpha = 0.1f),
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            AnimatedContent(targetState = uiState.currentStep) { step ->
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    when (step) {
                        0 -> StepOne(uiState, viewModel)
                        1 -> StepTwo(uiState, viewModel)
                        2 -> StepThree(uiState, viewModel)
                        3 -> StepFour(uiState, viewModel, onPickFolder = { 
                            try {
                                folderPickerLauncher.launch(Uri.fromFile(File(uiState.path))) 
                            } catch (e: Exception) {
                                folderPickerLauncher.launch(null)
                            }
                        })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StepOne(state: CreateServerState, viewModel: CreateServerViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Step Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Configuração Inicial", 
                    style = MaterialTheme.typography.labelMedium, 
                    color = Color.White.copy(0.5f)
                )
                Text(
                    "25%", 
                    style = MaterialTheme.typography.labelMedium, 
                    color = PrimaryDark,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(16.dp))
        }
        
        // Title
        item {
            Text(
                "Dê um nome ao seu mundo", 
                style = MaterialTheme.typography.headlineSmall, 
                color = Color.White, 
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
        }
        
        // Server Name Input
        item {
            Text(
                "Nome do Servidor",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(0.6f)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.name,
                onValueChange = { viewModel.updateName(it) },
                placeholder = { Text("Ex: Meu Servidor Survival", color = Color.White.copy(0.3f)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryDark,
                    focusedLabelColor = PrimaryDark,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                    unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                    cursorColor = PrimaryDark,
                    unfocusedContainerColor = SurfaceDark,
                    focusedContainerColor = SurfaceDark,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true
            )
            Spacer(Modifier.height(24.dp))
        }
        
        // Server Type Section Header
        item {
            Text(
                "Tipo de Servidor", 
                style = MaterialTheme.typography.titleMedium, 
                color = Color.White, 
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
        }
        
        // Server Type Cards
        items(ServerType.values().toList()) { type ->
            ServerTypeCard(
                type = type,
                isSelected = state.type == type,
                onClick = { viewModel.updateType(type) }
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ServerTypeCard(
    type: ServerType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val description = when (type) {
        ServerType.PAPER -> "Melhor performance e plugins"
        ServerType.FABRIC -> "Leve e modular"
        ServerType.VANILLA -> "Experiência original"
        ServerType.FORGE -> "Para mods clássicos"
        ServerType.NEOFORGE -> "Forge moderno"
        ServerType.BUKKIT -> "O clássico sistema de plugins"
        ServerType.SPIGOT -> "Otimizado para plugins"
    }
    
    val icon = when (type) {
        ServerType.PAPER -> Icons.Default.Speed
        ServerType.FABRIC -> Icons.Default.Layers
        ServerType.VANILLA -> Icons.Default.Grass
        ServerType.FORGE -> Icons.Default.Construction
        ServerType.NEOFORGE -> Icons.Default.AutoAwesome
        ServerType.BUKKIT -> Icons.Default.Inventory
        ServerType.SPIGOT -> Icons.Default.Bolt
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) PrimaryDark.copy(0.15f) else SurfaceDark
        ),
        border = if (isSelected) BorderStroke(1.5.dp, PrimaryDark) else BorderStroke(1.dp, Color.White.copy(0.08f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (isSelected) PrimaryDark.copy(0.2f) else Color.White.copy(0.05f),
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) PrimaryDark else Color.White.copy(0.7f),
                    modifier = Modifier.size(22.dp)
                )
            }
            
            Spacer(Modifier.width(14.dp))
            
            // Text content
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        type.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) PrimaryDark else Color.White
                    )

                }
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(0.5f)
                )
            }
            
            // Selection indicator
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = PrimaryDark,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}


@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StepTwo(state: CreateServerState, viewModel: CreateServerViewModel) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Step Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Configuração do Mundo", 
                    style = MaterialTheme.typography.labelMedium, 
                    color = Color.White.copy(0.5f)
                )
                Text(
                    "50%", 
                    style = MaterialTheme.typography.labelMedium, 
                    color = PrimaryDark,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        // --- SERVER ICON & IDENTITY ---
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    viewModel.updateServerIcon(uri)
                }

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceDark)
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                        .clickable { launcher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (state.serverIconUri != null) {
                        AsyncImage(
                            model = state.serverIconUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AddPhotoAlternate, null, tint = PrimaryDark, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.height(4.dp))
                            Text("Ícone", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.3f))
                        }
                    }
                }
                
                Spacer(Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text("Identidade Visual", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Escolha uma imagem para representar seu servidor na lista.", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // --- MOTD ---
        item {
            Text("MOTD (Mensagem do Dia)", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(0.7f))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.motd,
                onValueChange = { viewModel.updateMotd(it) },
                placeholder = { Text("A Minecraft Server") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryDark,
                    focusedLabelColor = PrimaryDark,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                    unfocusedContainerColor = SurfaceDark,
                    focusedContainerColor = SurfaceDark,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            
            // Preview Box
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Visibility, null, tint = Color.White.copy(0.3f), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Pré-visualização in-game", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.3f))
            }
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E1E1E)) // Minecraft server list background colorish
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(Color(0xFF55FF55), androidx.compose.foundation.shape.CircleShape)) // Fake ping indicator
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = com.lzofseven.mcserver.util.MotdUtils.parseMinecraftColors(state.motd),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // --- VERSION SELECTION ---
        item {
            Text("Versão do Minecraft", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(0.7f))
            Spacer(Modifier.height(8.dp))
            
            var expanded by remember { mutableStateOf(false) }
            val versions = state.availableVersions

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = state.version,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryDark,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                            unfocusedContainerColor = SurfaceDark,
                            focusedContainerColor = SurfaceDark,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    // Overlay to capture clicks specifically for the dropdown
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
                    versions.forEach { version ->
                        DropdownMenuItem(
                            text = { Text(version, color = Color.White) },
                            onClick = {
                                viewModel.updateVersion(version)
                                expanded = false
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // --- DIFFICULTY & GAMEMODE ---
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Difficulty
                Column {
                    Text("Dificuldade", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(0.7f))
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Peaceful", "Easy", "Normal", "Hard").forEach { diff ->
                            FilterChip(
                                selected = state.difficulty == diff,
                                onClick = { viewModel.updateDifficulty(diff) },
                                label = { Text(diff) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryDark.copy(alpha = 0.2f),
                                    selectedLabelColor = PrimaryDark,
                                    labelColor = Color.White.copy(0.7f),
                                    containerColor = SurfaceDark
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = if (state.difficulty == diff) PrimaryDark else Color.White.copy(0.1f),
                                    enabled = true,
                                    selected = state.difficulty == diff
                                )
                            )
                        }
                    }
                }

                // GameMode
                Column {
                    Text("Modo de Jogo", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(0.7f))
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Survival", "Creative", "Adventure", "Spectator").forEach { mode ->
                            FilterChip(
                                selected = state.gameMode == mode,
                                onClick = { viewModel.updateGameMode(mode) },
                                label = { Text(mode) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryDark.copy(alpha = 0.2f),
                                    selectedLabelColor = PrimaryDark,
                                    labelColor = Color.White.copy(0.7f),
                                    containerColor = SurfaceDark
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = if (state.gameMode == mode) PrimaryDark else Color.White.copy(0.1f),
                                    enabled = true,
                                    selected = state.gameMode == mode
                                )
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // --- TOGGLES & RAM ---
        item {
            // Cracked Mode Toggle Card
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(0.05f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Modo Pirata (Cracked)", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Permite jogadores sem conta original.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.5f))
                    }
                    Switch(
                        checked = !state.onlineMode,
                        onCheckedChange = { viewModel.updateOnlineMode(!it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PrimaryDark,
                            uncheckedThumbColor = Color.White.copy(0.6f),
                            uncheckedTrackColor = Color.Black.copy(0.5f),
                            uncheckedBorderColor = Color.Transparent
                        )
                    )
                }
            }
            Spacer(Modifier.height(24.dp))

            // RAM Slider
            val gb = state.ramAllocation / 1024f
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text("Alocação de RAM", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(0.7f))
                Text("${String.format("%.1f", gb)} GB", style = MaterialTheme.typography.titleMedium, color = PrimaryDark, fontWeight = FontWeight.Bold)
            }
            
            Spacer(Modifier.height(8.dp))
            
            Slider(
                value = state.ramAllocation.toFloat(),
                onValueChange = { viewModel.updateRam(it.toInt()) },
                valueRange = 1024f..8192f,
                steps = 13,
                colors = SliderDefaults.colors(thumbColor = PrimaryDark, activeTrackColor = PrimaryDark, inactiveTrackColor = Color.White.copy(0.1f))
            )
            Text("Recomendado: 2.0GB+ para versões 1.18+", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.3f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StepThree(state: CreateServerState, viewModel: CreateServerViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Step Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Configurações Avançadas", 
                    style = MaterialTheme.typography.labelMedium, 
                    color = Color.White.copy(0.5f)
                )
                Text(
                    "75%", 
                    style = MaterialTheme.typography.labelMedium, 
                    color = PrimaryDark,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        // --- SYSTEM SETTINGS ---
        item {
            Text("Sistema", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(0.7f))
            Spacer(Modifier.height(8.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Java Version Selector
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(0.05f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Terminal, null, tint = PrimaryDark, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Versão do Java", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(11, 17, 21).forEach { ver ->
                                FilterChip(
                                    selected = state.javaVersion == ver,
                                    onClick = { viewModel.updateJavaVersion(ver) },
                                    label = { Text("Java $ver") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PrimaryDark.copy(alpha = 0.2f),
                                        selectedLabelColor = PrimaryDark,
                                        labelColor = Color.White.copy(0.7f),
                                        containerColor = BackgroundDark
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        borderColor = if (state.javaVersion == ver) PrimaryDark else Color.White.copy(0.1f),
                                        enabled = true,
                                        selected = state.javaVersion == ver
                                    )
                                )
                            }
                        }
                    }
                }

                // AutoStart Toggle
                ToggleCard(
                    title = "Início Automático",
                    subtitle = "Iniciar servidor ao abrir o app",
                    icon = Icons.Default.Launch,
                    checked = state.autoStart,
                    onCheckedChange = { viewModel.updateAutoStart(it) }
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        // --- GAMEPLAY TOGGLES ---
        item {
            Text("Regras de Gameplay", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(0.7f))
            Spacer(Modifier.height(8.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Allow Flight
                ToggleCard(
                    title = "Permitir Voo",
                    subtitle = "Habilita o uso de fly hacks ou mods de voo",
                    icon = Icons.Default.FlightTakeoff,
                    checked = state.allowFlight,
                    onCheckedChange = { viewModel.updateAdvSettings(allowFlight = it) }
                )
                
                // PvP
                ToggleCard(
                    title = "Habilitar PvP",
                    subtitle = "Combate entre jogadores",
                    icon = Icons.Default.Security,
                    checked = state.pvp,
                    onCheckedChange = { viewModel.updateAdvSettings(pvp = it) }
                )
                
                // Spawn Animals & NPCs
                ToggleCard(
                    title = "Habilitar Mobs",
                    subtitle = "Spawn de animais e monstros",
                    icon = Icons.Default.Pets,
                    checked = state.spawnAnimals, // Simplification: using spawnAnimals toggle for both for now or split
                    onCheckedChange = { 
                        viewModel.updateAdvSettings(spawnAnimals = it, spawnNpcs = it) 
                    }
                )
                
                // Structures
                ToggleCard(
                    title = "Gerar Estruturas",
                    subtitle = "Vilas, templos e dungeons",
                    icon = Icons.Default.Castle,
                    checked = state.generateStructures,
                    onCheckedChange = { viewModel.updateAdvSettings(generateStructures = it) }
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        // --- DIMENSIONS ---
        item {
            Text("Dimensões", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(0.7f))
            Spacer(Modifier.height(8.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Nether
                ToggleCard(
                    title = "Nether",
                    subtitle = "Ativar dimensão do Nether",
                    icon = Icons.Default.LocalFireDepartment,
                    checked = state.allowNether,
                    onCheckedChange = { viewModel.updateAdvSettings(allowNether = it) }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
        
        // ... (Limits section remains same)
        item {
            Text("Limites do Servidor", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(0.7f))
            Spacer(Modifier.height(12.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Max Players
                OutlinedTextField(
                    value = state.maxPlayers.toString(),
                    onValueChange = { if (it.all { char -> char.isDigit() }) viewModel.updateAdvSettings(maxPlayers = it.toIntOrNull() ?: 20) },
                    label = { Text("Max Players") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryDark,
                        unfocusedBorderColor = Color.White.copy(0.1f),
                        unfocusedContainerColor = SurfaceDark,
                        focusedContainerColor = SurfaceDark,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                
                // View Distance
                OutlinedTextField(
                    value = state.viewDistance.toString(),
                    onValueChange = { if (it.all { char -> char.isDigit() }) viewModel.updateAdvSettings(viewDistance = it.toIntOrNull() ?: 10) },
                    label = { Text("View Distance") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryDark,
                        unfocusedBorderColor = Color.White.copy(0.1f),
                        unfocusedContainerColor = SurfaceDark,
                        focusedContainerColor = SurfaceDark,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }
        }
    }
}

// ... ToggleCard remains same ... (omitted from replace chunk as it's separate)

@Composable
private fun ToggleCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (checked) PrimaryDark.copy(0.3f) else Color.White.copy(0.05f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(if (checked) PrimaryDark.copy(0.2f) else Color.White.copy(0.05f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = if (checked) PrimaryDark else Color.White.copy(0.5f), modifier = Modifier.size(20.dp))
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.displaySmall, fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp), color = Color.White.copy(0.5f), lineHeight = androidx.compose.ui.unit.TextUnit(14f, androidx.compose.ui.unit.TextUnitType.Sp))
            }
            
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = PrimaryDark,
                    uncheckedThumbColor = Color.White.copy(0.6f),
                    uncheckedTrackColor = Color.Black.copy(0.3f),
                    uncheckedBorderColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
fun StepFour(state: CreateServerState, viewModel: CreateServerViewModel, onPickFolder: () -> Unit) {
    val isSafUri = state.path.startsWith("content://")
    
    // Step Header
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Instalação", 
            style = MaterialTheme.typography.labelMedium, 
            color = Color.White.copy(0.5f)
        )
        Text(
            "100%", 
            style = MaterialTheme.typography.labelMedium, 
            color = PrimaryDark,
            fontWeight = FontWeight.Bold
        )
    }
    
    Spacer(Modifier.height(24.dp))

    // --- FOLDER CARD ---
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPickFolder() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = if (isSafUri) BorderStroke(1.dp, PrimaryDark) else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Folder Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(PrimaryDark.copy(0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = PrimaryDark,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(Modifier.width(14.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "DIRETÓRIO ATUAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(0.5f),
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                
                // Path Decoding Logic
                val displayPath = remember(state.path) {
                    if (isSafUri) {
                        try {
                            val uri = android.net.Uri.parse(state.path)
                            // Decode URL encoding (e.g. %20 -> space, %2F -> /)
                            val lastSegment = java.net.URLDecoder.decode(uri.lastPathSegment ?: state.path, "UTF-8")
                            // Clean up SAF specific prefixes
                            "/${lastSegment.replace("primary:", "").replace(":", "/")}"
                        } catch (e: Exception) { 
                            state.path 
                        }
                    } else "Toque para selecionar"
                }

                Text(
                    displayPath,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSafUri) Color.White else Color.White.copy(0.4f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = PrimaryDark,
                modifier = Modifier.size(24.dp)
            )
        }
    }
    
    Spacer(Modifier.height(24.dp))
    
    // --- SUMMARY CARD ---
    Text(
        "Resumo do Servidor",
        style = MaterialTheme.typography.titleMedium,
        color = Color.White,
        fontWeight = FontWeight.Bold
    )
    
    Spacer(Modifier.height(12.dp))
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SummaryRow(
                icon = Icons.Default.Label,
                label = "Nome",
                value = state.name.ifBlank { "Sem nome" }
            )
            
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = Color.White.copy(0.06f)
            )
            
            SummaryRow(
                icon = Icons.Default.Dns,
                label = "Tipo",
                value = state.type.displayName
            )
            
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = Color.White.copy(0.06f)
            )
            
            SummaryRow(
                icon = Icons.Default.Tag,
                label = "Versão",
                value = state.version
            )
            
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = Color.White.copy(0.06f)
            )
            
            SummaryRow(
                icon = Icons.Default.Memory,
                label = "RAM",
                value = "${String.format("%.1f", state.ramAllocation / 1024f)} GB",
                valueColor = PrimaryDark
            )
            
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = Color.White.copy(0.06f)
            )
            
            // Gameplay Rules
            SummaryRow(
                icon = Icons.Default.SportsEsports,
                label = "Modo de Jogo",
                value = state.gameMode
            )
            
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = Color.White.copy(0.06f)
            )
            
            SummaryRow(
                icon = Icons.Default.Warning,
                label = "Dificuldade",
                value = state.difficulty
            )
            
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = Color.White.copy(0.06f)
            )
            
            // Toggles summary row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToggleBadge("PvP", state.pvp)
                ToggleBadge("Voo", state.allowFlight)
                ToggleBadge("Mobs", state.spawnAnimals)
                ToggleBadge("Nether", state.allowNether)
            }
        }
    }
    
    Spacer(Modifier.height(16.dp))
    
    if (!isSafUri) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = Color(0xFFFF9800),
                modifier = Modifier.size(16.dp)
            )
            Text(
                "Selecione uma pasta para criar o servidor",
                color = Color(0xFFFF9800),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}



@Composable
private fun SummaryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    valueColor: Color = Color.White
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(0.4f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(0.6f),
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ToggleBadge(label: String, enabled: Boolean) {
    Box(
        modifier = Modifier
            .background(
                if (enabled) PrimaryDark.copy(0.2f) else Color.White.copy(0.05f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        if (enabled) PrimaryDark else Color.White.copy(0.3f),
                        androidx.compose.foundation.shape.CircleShape
                    )
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) PrimaryDark else Color.White.copy(0.5f),
                fontWeight = FontWeight.Medium
            )
        }
    }
}
