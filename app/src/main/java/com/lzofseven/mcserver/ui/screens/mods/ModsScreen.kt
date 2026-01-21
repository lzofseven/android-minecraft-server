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
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ModsScreen(
    navController: NavController,
    viewModel: ModsViewModel = hiltViewModel()
) {
    val content by viewModel.installedContent.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val selectedSubFilter by viewModel.selectedSubFilter.collectAsState()
    val showDisabled by viewModel.showDisabled.collectAsState()
    val showUpdates by viewModel.showUpdates.collectAsState()
    
    val tabs = listOf("Content", "Worlds", "Logs")
    
    var searchQuery by androidx.compose.runtime.mutableStateOf("")
    val filteredContent = content.filter { it.name.contains(searchQuery, ignoreCase = true) || it.fileName.contains(searchQuery, ignoreCase = true) }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            Column(modifier = Modifier.background(BackgroundDark).padding(top = 16.dp)) {
                // Pill Tabs
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tabs.forEachIndexed { index, title ->
                        Surface(
                            onClick = { viewModel.selectTab(index) },
                            color = if (selectedTab == index) Color(0xFF1E2924) else Color.Transparent,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp)) {
                                Text(
                                    text = title,
                                    color = if (selectedTab == index) PrimaryDark else Color.White.copy(alpha = 0.6f),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Search & Install Row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f).height(48.dp),
                        placeholder = { Text("Search ${content.size} projects...", color = Color.White.copy(0.3f), fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(18.dp)) },
                        trailingIcon = { if(searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(androidx.compose.material.icons.filled.Close, null, tint = Color.White.copy(0.4f)) } },
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.White.copy(0.1f),
                            unfocusedBorderColor = Color.White.copy(0.1f),
                            unfocusedContainerColor = Color.White.copy(0.05f),
                            focusedContainerColor = Color.White.copy(0.08f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                    )

                    // Install content button
                    Surface(
                        onClick = { navController.navigate(Screen.Library.createRoute(viewModel.serverId)) },
                        color = Color.White.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                            Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Install content", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(Modifier.width(8.dp))
                            Icon(androidx.compose.material.icons.filled.KeyboardArrowDown, null, tint = Color.White.copy(0.4f), modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Sub-filter Chips
                if (selectedTab == 0) {
                    val subFilters = listOf(
                        null to "Mods",
                        "shader" to "Shaders",
                        "resourcepack" to "Resource Packs"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        subFilters.forEach { (type, label) ->
                            FilterChip(
                                selected = selectedSubFilter == type,
                                onClick = { viewModel.setSubFilter(type) },
                                label = { Text(label, fontSize = 12.sp) },
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = Color.White.copy(0.05f),
                                    labelColor = Color.White.copy(0.6f),
                                    selectedContainerColor = Color.White.copy(0.15f),
                                    selectedLabelColor = Color.White
                                ),
                                border = null,
                                leadingIcon = if (label == "Mods") { { Icon(androidx.compose.material.icons.filled.FilterList, null, modifier = Modifier.size(14.dp)) } } else null
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Table Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(androidx.compose.material.icons.filled.CheckBoxOutlineBlank, null, tint = Color.White.copy(0.2f), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Name", color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.width(180.dp))
                    Text("Updated", color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(androidx.compose.material.icons.filled.Refresh, null, tint = Color.White.copy(0.4f), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Refresh", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
                        Spacer(Modifier.width(16.dp))
                        Icon(androidx.compose.material.icons.filled.Download, null, tint = PrimaryDark, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Update all", color = PrimaryDark, fontSize = 12.sp)
                    }
                }
                
                Divider(color = Color.White.copy(alpha = 0.05f), thickness = 1.dp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    ) { padding ->
        if (selectedTab == 2) {
             Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                 Text("Logs do Servidor (Em breve)", color = Color.White.copy(0.3f))
             }
             return@Scaffold
        }

        if (filteredContent.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = when(selectedTab) {
                            0 -> Icons.Default.Extension
                            else -> Icons.Default.Public
                        },
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.1f),
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "Nenhum resultado para '$searchQuery'" else "Vazio por aqui",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.4f)
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
                items(filteredContent) { item ->
                    ContentItem(
                        item = item,
                        onToggle = { viewModel.toggleItem(item) },
                        onDelete = { viewModel.deleteItem(item) }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
fun ContentItem(item: InstalledContent, onToggle: () -> Unit, onDelete: () -> Unit) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox
            Icon(androidx.compose.material.icons.filled.CheckBoxOutlineBlank, null, tint = Color.White.copy(0.1f), modifier = Modifier.size(18.dp))
            
            Spacer(Modifier.width(16.dp))

            // Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                if (item.iconUrl != null) {
                    coil.compose.AsyncImage(
                        model = item.iconUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = when(item.type) {
                            "mod" -> Icons.Default.Extension
                            "plugin" -> Icons.Default.SettingsInputComponent
                            else -> Icons.Default.Public
                        },
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            // Name & Author
            Column(modifier = Modifier.width(180.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (item.isEnabled) Color.White else Color.White.copy(alpha = 0.3f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = "by ${item.author}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.4f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            // Version & Filename
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = item.version,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.3f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    fontSize = 10.sp
                )
            }
            
            Spacer(Modifier.width(16.dp))

            // Actions Row
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Download icon for updates (if applicable)
                if (false) { // Placeholder for hasUpdate
                    Icon(androidx.compose.material.icons.filled.FileDownload, null, tint = PrimaryDark, modifier = Modifier.size(20.dp))
                }
                
                Switch(
                    checked = item.isEnabled,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.scale(0.8f),
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = PrimaryDark,
                        checkedThumbColor = BackgroundDark,
                        uncheckedTrackColor = Color.White.copy(0.1f),
                        uncheckedThumbColor = Color.White.copy(0.4f),
                        uncheckedBorderColor = Color.Transparent
                    )
                )

                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(
                        androidx.compose.material.icons.filled.DeleteOutline,
                        contentDescription = "Remover",
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                
                Icon(androidx.compose.material.icons.filled.MoreVert, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

