package com.lzofseven.mcserver.ui.screens.moddetails

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.compose.foundation.clickable
import coil.compose.AsyncImage
import com.lzofseven.mcserver.data.model.ModrinthFile
import com.lzofseven.mcserver.data.model.ModrinthVersion
import com.lzofseven.mcserver.ui.theme.BackgroundDark
import com.lzofseven.mcserver.ui.theme.PrimaryDark
import com.lzofseven.mcserver.ui.theme.SurfaceDark

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ModDetailsScreen(
    navController: NavController,
    viewModel: ModDetailsViewModel = hiltViewModel()
) {
    val project by viewModel.project.collectAsState()
    val versions by viewModel.versions.collectAsState()
    val availableGameVersions by viewModel.availableGameVersions.collectAsState()
    val selectedGameVersion by viewModel.selectedGameVersion.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val downloadProgressMap by viewModel.downloadProgressMap.collectAsState()
    val compatibleLoaders by viewModel.compatibleLoaders.collectAsState()
    val pendingDownload by viewModel.pendingDownload.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(key1 = true) {
        viewModel.toastMessage.collect { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // File Selection Dialog
    if (pendingDownload != null) {
        FileSelectionDialog(
            files = pendingDownload!!.files,
            onFileSelected = { file ->
                viewModel.downloadSpecificFile(pendingDownload!!.uiVersion, file)
            },
            onDismiss = { viewModel.dismissFileSelection() }
        )
    }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = { Text(project?.title ?: "Detalhes", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Voltar", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryDark)
            }
        } else {
            project?.let { proj ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = proj.iconUrl ?: "https://lh3.googleusercontent.com/aida-public/AB6AXuCwfrVy1tVAb2P64nfILPuylYzDWefOwHF251qSRzEp0xrLw1zRuRyS1TwbUVGstZ7Hd1h2lbIWTmme9atM1kDq7MA2HuNRk_yVkIXPkN8VK6On77IKdxXB2_HoP2CAXvSMbAAMV_Q_ixgnlis_A-slL40GFyzmbuW1fEzujtubzwlNfDK6OeB8ZUzFj6yTwMyXVU2ii1r9OgmjVTSm1WxffLOFkgNmowvuLCfRgynW10vEv7kq7F42ttsHnsnXYjJtlLUCJYlNBi_p",
                                contentDescription = null,
                                modifier = Modifier
                                    .size(80.dp)
                                    .background(SurfaceDark, RoundedCornerShape(16.dp))
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(proj.title, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(proj.description, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.7f))
                                Spacer(Modifier.height(8.dp))
                                Text("Downloads: ${formatDownloads(proj.downloads)}", style = MaterialTheme.typography.labelSmall, color = PrimaryDark)
                            }
                        }
                    }

                    // Version Filter Dropdown
                    if (availableGameVersions.size > 1) {
                        item {
                            var expanded by remember { mutableStateOf(false) }
                            
                            Column {
                                Text("Filtrar por Versão", style = MaterialTheme.typography.titleSmall, color = Color.White.copy(0.7f), modifier = Modifier.padding(top = 8.dp))
                                Spacer(Modifier.height(8.dp))
                                
                                ExposedDropdownMenuBox(
                                    expanded = expanded,
                                    onExpandedChange = { expanded = !expanded },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        OutlinedTextField(
                                            value = selectedGameVersion ?: "Todas as Versões",
                                            onValueChange = {},
                                            readOnly = true,
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = PrimaryDark,
                                                unfocusedBorderColor = Color.White.copy(0.2f),
                                                focusedContainerColor = SurfaceDark,
                                                unfocusedContainerColor = SurfaceDark,
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White
                                            )
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
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Todas as Versões (${viewModel.versions.value.size})", color = Color.White) },
                                            onClick = {
                                                viewModel.selectGameVersion(null)
                                                expanded = false
                                            }
                                        )
                                        availableGameVersions.forEach { version ->
                                            DropdownMenuItem(
                                                text = { Text(version, color = Color.White) },
                                                onClick = {
                                                    viewModel.selectGameVersion(version)
                                                    expanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Loader Filter (NEW)
                    item {
                        val selectedLoader by viewModel.selectedLoader.collectAsState()
                        Column {
                            Text("Filtrar por Loader", style = MaterialTheme.typography.titleSmall, color = Color.White.copy(0.7f))
                            Spacer(Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val loaders = listOf(null to "Todos", "fabric" to "Fabric", "forge" to "Forge", "neoforge" to "NeoForge", "paper" to "Paper", "bukkit" to "Bukkit", "spigot" to "Spigot")
                                items(loaders) { (loader, label) ->
                                    FilterChip(
                                        selected = selectedLoader == loader,
                                        onClick = { viewModel.setLoaderFilter(loader) },
                                        label = { Text(label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = PrimaryDark,
                                            selectedLabelColor = BackgroundDark,
                                            containerColor = SurfaceDark,
                                            labelColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Versions List Title
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Versões Compatíveis (${versions.size})", 
                                style = MaterialTheme.typography.titleMedium, 
                                color = Color.White, 
                                fontWeight = FontWeight.Bold
                            )
                            
                            val showAlphaBeta by viewModel.showAlphaBeta.collectAsState()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Beta/Alpha", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.4f))
                                Switch(
                                    checked = showAlphaBeta,
                                    onCheckedChange = { viewModel.toggleAlphaBeta() },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = PrimaryDark,
                                        checkedTrackColor = PrimaryDark.copy(alpha = 0.3f),
                                        uncheckedThumbColor = Color.White.copy(alpha = 0.4f),
                                        uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                                    ),
                                    modifier = Modifier.scale(0.7f)
                                )
                            }
                        }
                    }

                    if (versions.isEmpty()) {
                        item {
                            Text("Nenhuma versão encontrada para este filtro.", color = Color.White.copy(0.5f))
                        }
                    } else {
                        items(versions) { uiVersion ->
                            val isCompatible = compatibleLoaders.isEmpty() || uiVersion.loader in compatibleLoaders
                            VersionItem(uiVersion, downloadProgressMap[uiVersion.original.id], isCompatible) {
                                viewModel.downloadVersion(uiVersion)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VersionItem(uiVersion: ModDetailsViewModel.UiVersion, progress: Float?, isCompatible: Boolean, onDownload: () -> Unit) {
    val version = uiVersion.original
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(version.versionNumber, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Surface(color = PrimaryDark.copy(0.1f), shape = RoundedCornerShape(4.dp), modifier = Modifier.padding(start = 8.dp)) {
                        Text(uiVersion.loader.replaceFirstChar { it.uppercase() }, color = PrimaryDark, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
                    }
                    if (!isCompatible) {
                        Spacer(Modifier.width(8.dp))
                        Surface(color = com.lzofseven.mcserver.ui.theme.ErrorDark.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                            Text("Incompatível", color = com.lzofseven.mcserver.ui.theme.ErrorDark, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                        }
                    }
                }
                Text(
                    "${version.gameVersions.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(0.5f)
                )
            }

            if (progress != null) {
                CircularProgressIndicator(progress = progress, color = PrimaryDark, modifier = Modifier.size(24.dp))
            } else {
                IconButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, contentDescription = "Download", tint = PrimaryDark)
                }
            }
        }
    }
}

private fun formatDownloads(downloads: Int): String {
    return when {
        downloads >= 1_000_000 -> "${downloads / 1_000_000}M"
        downloads >= 1_000 -> "${downloads / 1_000}K"
        else -> downloads.toString()
    }
}

@Composable
fun FileSelectionDialog(
    files: List<ModrinthFile>,
    onFileSelected: (ModrinthFile) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = {
            Text(
                "Escolha o Arquivo",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Esta versão possui múltiplos arquivos. Escolha o correto para seu servidor:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(0.7f)
                )
                Spacer(Modifier.height(8.dp))
                
                files.forEachIndexed { index, file ->
                    val loaderHint = when {
                        file.filename.contains("paper", ignoreCase = true) -> "Paper"
                        file.filename.contains("spigot", ignoreCase = true) -> "Spigot"
                        file.filename.contains("bukkit", ignoreCase = true) -> "Bukkit"
                        file.filename.contains("fabric", ignoreCase = true) -> "Fabric"
                        file.filename.contains("forge", ignoreCase = true) -> "Forge"
                        file.filename.contains("neoforge", ignoreCase = true) -> "NeoForge"
                        file.primary -> "Principal"
                        else -> null
                    }
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onFileSelected(file) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (file.primary) PrimaryDark.copy(0.15f) else BackgroundDark
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    file.filename,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${file.size / 1024} KB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(0.5f)
                                    )
                                    if (loaderHint != null) {
                                        Surface(
                                            color = PrimaryDark.copy(0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                loaderHint,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = PrimaryDark,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Baixar",
                                tint = PrimaryDark,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color.White.copy(0.7f))
            }
        }
    )
}
