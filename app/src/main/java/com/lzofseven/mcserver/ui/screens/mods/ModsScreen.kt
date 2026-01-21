package com.lzofseven.mcserver.ui.screens.mods

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.lzofseven.mcserver.ui.navigation.Screen
import com.lzofseven.mcserver.ui.theme.BackgroundDark
import com.lzofseven.mcserver.ui.theme.PrimaryDark
import com.lzofseven.mcserver.ui.theme.SurfaceDark
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModsScreen(
    navController: NavController,
    viewModel: ModsViewModel = hiltViewModel()
) {
    val mods by viewModel.installedMods.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mods Instalados") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        containerColor = BackgroundDark,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { 
                    val serverId = viewModel.serverId // I added serverId to VM
                    navController.navigate(Screen.Library.createRoute(serverId)) 
                },
                containerColor = PrimaryDark,
                contentColor = BackgroundDark,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("ADICIONAR MOD", fontWeight = FontWeight.Black) }
            )
        }
    ) { padding ->
        if (mods.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Nenhum mod instalado",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Toque em + para adicionar da biblioteca",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(mods) { mod ->
                    ModItem(
                        mod = mod,
                        onToggle = { viewModel.toggleMod(mod) },
                        onDelete = { viewModel.deleteMod(mod) }
                    )
                }
            }
        }
    }
}

@Composable
fun ModItem(mod: InstalledMod, onToggle: () -> Unit, onDelete: () -> Unit) {
    Surface(
        color = SurfaceDark.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mod/Plugin Indicator
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (mod.loader == "mod") Color(0xFF64B5F6).copy(alpha = 0.2f) else Color(0xFF81C784).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (mod.loader == "mod") Icons.Default.Extension else Icons.Default.SettingsInputComponent,
                    contentDescription = null,
                    tint = if (mod.loader == "mod") Color(0xFF64B5F6) else Color(0xFF81C784),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mod.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (mod.isEnabled) Color.White else Color.White.copy(alpha = 0.3f)
                )
                Text(
                    text = "${mod.loader.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = PrimaryDark,
                    fontWeight = FontWeight.Black
                )
            }

            Switch(
                checked = mod.isEnabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(checkedTrackColor = PrimaryDark)
            )

            Spacer(Modifier.width(8.dp))

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remover",
                    tint = Color.Red.copy(alpha = 0.6f)
                )
            }
        }
    }
}

data class InstalledMod(
    val name: String,
    val version: String,
    val loader: String,
    val fileName: String,
    val isEnabled: Boolean,
    val fullPath: String
)
