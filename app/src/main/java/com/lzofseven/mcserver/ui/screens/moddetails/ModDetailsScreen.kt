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
import coil.compose.AsyncImage
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
    val context = LocalContext.current

    LaunchedEffect(key1 = true) {
        viewModel.toastMessage.collect { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
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
                                    ExposedDropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false },
                                        modifier = Modifier.background(SurfaceDark)
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
                        items(versions) { version ->
                            val isCompatible = compatibleLoaders.isEmpty() || version.loaders.any { it in compatibleLoaders }
                            VersionItem(version, downloadProgressMap[version.id], isCompatible) {
                                viewModel.downloadVersion(version)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VersionItem(version: ModrinthVersion, progress: Float?, isCompatible: Boolean, onDownload: () -> Unit) {
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
                    if (!isCompatible) {
                        Spacer(Modifier.width(8.dp))
                        Surface(color = com.lzofseven.mcserver.ui.theme.ErrorDark.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                            Text("Incompatível", color = com.lzofseven.mcserver.ui.theme.ErrorDark, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                        }
                    }
                }
                Text(
                    "${version.gameVersions.joinToString(", ")} • ${version.loaders.joinToString(", ")}",
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
