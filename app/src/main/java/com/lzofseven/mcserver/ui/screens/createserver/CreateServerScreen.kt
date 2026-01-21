package com.lzofseven.mcserver.ui.screens.createserver

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.lzofseven.mcserver.ui.screens.config.ServerType
import com.lzofseven.mcserver.ui.theme.BackgroundDark
import com.lzofseven.mcserver.ui.theme.PrimaryDark
import com.lzofseven.mcserver.ui.theme.SurfaceDark

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun CreateServerScreen(
    navController: NavController,
    viewModel: CreateServerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // Persist permission
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.updatePath(it.toString())
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
                    .padding(16.dp),
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
                        if (uiState.currentStep < 2) {
                            viewModel.nextStep()
                        } else {
                            viewModel.createServer {
                                navController.popBackStack()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryDark),
                    enabled = (uiState.currentStep != 0 || uiState.name.isNotBlank()) &&
                              (uiState.currentStep != 2 || uiState.path.isNotBlank())
                ) {
                    Text(if (uiState.currentStep == 2) "Criar Servidor" else "Próximo")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(horizontal = 24.dp)) {
            // Progress Indicator
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { index ->
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
                        2 -> StepThree(uiState, viewModel, onPickFolder = { folderPickerLauncher.launch(null) })
                    }
                }
            }
        }
    }
}

@Composable
fun StepOne(state: CreateServerState, viewModel: CreateServerViewModel) {
    Text("Nome e Tipo", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
    
    OutlinedTextField(
        value = state.name,
        onValueChange = { viewModel.updateName(it) },
        label = { Text("Nome do Servidor") },
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PrimaryDark,
            focusedLabelColor = PrimaryDark,
            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
            unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
            cursorColor = PrimaryDark,
            unfocusedContainerColor = SurfaceDark,
            focusedContainerColor = SurfaceDark
        )
    )

    Text("Tipo de Software", style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.padding(top = 16.dp))
    
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ServerType.values().forEach { type ->
            FilterChip(
                selected = state.type == type,
                onClick = { viewModel.updateType(type) },
                label = { Text(type.displayName) },
                leadingIcon = if (state.type == type) { { Icon(Icons.Default.Check, null) } } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PrimaryDark.copy(alpha = 0.2f),
                    selectedLabelColor = PrimaryDark,
                    labelColor = Color.White
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepTwo(state: CreateServerState, viewModel: CreateServerViewModel) {
    Text("Configuração do Mundo", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
    
    // Version Selector
    var expanded by remember { mutableStateOf(false) }
    val versions = listOf("1.21.1", "1.20.4", "1.20.1", "1.19.4", "1.18.2", "1.16.5", "1.12.2", "1.8.9")

    Spacer(Modifier.height(16.dp))
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = state.version,
            onValueChange = {},
            readOnly = true,
            label = { Text("Versão do Minecraft") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryDark,
                focusedLabelColor = PrimaryDark,
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                cursorColor = PrimaryDark,
                unfocusedContainerColor = SurfaceDark,
                focusedContainerColor = SurfaceDark,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(SurfaceDark)
        ) {
            versions.forEach { version ->
                DropdownMenuItem(
                    text = { Text(version, color = Color.White) },
                    onClick = {
                        viewModel.updateVersion(version)
                        expanded = false
                    }
                )
            }
        }
    }

    Spacer(Modifier.height(24.dp))
    
    // Difficulty
    Text("Dificuldade", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        listOf("Peaceful", "Easy", "Normal", "Hard").forEach { diff ->
            FilterChip(
                selected = state.difficulty == diff,
                onClick = { viewModel.updateDifficulty(diff) },
                label = { Text(diff) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PrimaryDark.copy(alpha = 0.2f),
                    selectedLabelColor = PrimaryDark,
                    labelColor = Color.White
                )
            )
        }
    }

    Spacer(Modifier.height(16.dp))
    
    // GameMode & Online
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("Modo de Jogo", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                listOf("Survival", "Creative").forEach { mode ->
                    FilterChip(
                        selected = state.gameMode == mode,
                        onClick = { viewModel.updateGameMode(mode) },
                        label = { Text(mode) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryDark.copy(alpha = 0.2f),
                            selectedLabelColor = PrimaryDark,
                            labelColor = Color.White
                        )
                    )
                }
            }
        }
        
        Column(horizontalAlignment = Alignment.End) {
             Text("Pirata (Cracked)", style = MaterialTheme.typography.titleSmall, color = if (!state.onlineMode) PrimaryDark else Color.White.copy(0.7f), fontWeight = FontWeight.Bold)
             Switch(
                 checked = !state.onlineMode,
                 onCheckedChange = { viewModel.updateOnlineMode(!it) },
                 colors = SwitchDefaults.colors(
                     checkedThumbColor = BackgroundDark,
                     checkedTrackColor = PrimaryDark,
                     uncheckedThumbColor = Color.White,
                     uncheckedTrackColor = SurfaceDark
                 )
             )
        }
    }

    Spacer(modifier = Modifier.height(24.dp))
    
    // RAM
    val gb = state.ramAllocation / 1024f
    Text("Alocação de RAM: ${String.format("%.1f", gb)} GB", color = Color.White, fontWeight = FontWeight.Bold)
    Text("Recomendado: 2.0GB+ para versões 1.18+", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.5f))
    
    Slider(
        value = state.ramAllocation.toFloat(),
        onValueChange = { viewModel.updateRam(it.toInt()) },
        valueRange = 1024f..8192f,
        steps = 13, // 0.5GB steps approx
        colors = SliderDefaults.colors(thumbColor = PrimaryDark, activeTrackColor = PrimaryDark, inactiveTrackColor = Color.White.copy(0.1f))
    )
}

@Composable
fun StepThree(state: CreateServerState, viewModel: CreateServerViewModel, onPickFolder: () -> Unit) {
    Text("Instalação", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
    Text("Escolha onde os arquivos do servidor serão vistos e salvos.", color = Color.White.copy(alpha = 0.7f))
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPickFolder() }
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.FolderOpen, null, tint = PrimaryDark)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = if (state.path.isEmpty()) "Selecionar Pasta" else "Pasta Selecionada",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (state.path.isNotEmpty()) {
                    Text(
                        text = state.path,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
