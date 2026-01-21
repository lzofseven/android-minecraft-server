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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.lzofseven.mcserver.ui.navigation.Screen
import com.lzofseven.mcserver.ui.theme.BackgroundDark
import com.lzofseven.mcserver.ui.theme.PrimaryDark
import com.lzofseven.mcserver.ui.theme.SurfaceDark
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModsScreen(
    navController: NavController,
    viewModel: ModsViewModel = hiltViewModel()
) {
    val content by viewModel.installedContent.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val selectedSubFilter by viewModel.selectedSubFilter.collectAsState()
    
    val tabs = listOf("Content", "Worlds", "Logs")
    
    var searchQuery by remember { mutableStateOf("") }
    val filteredContent = content.filter { it.name.contains(searchQuery, ignoreCase = true) || it.fileName.contains(searchQuery, ignoreCase = true) }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            Column(modifier = Modifier.background(BackgroundDark).padding(top = 16.dp)) {
                // Tab Row
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = PrimaryDark,
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { viewModel.selectTab(index) },
                            text = {
                                Text(
                                    text = title,
                                    color = if (selectedTab == index) PrimaryDark else Color.White.copy(alpha = 0.6f),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        )
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
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Sub-filter Chips
                if (selectedTab == 0) {
                    val subFilters = listOf(
                        null to "All",
                        "mod" to "Mods",
                        "plugin" to "Plugins"
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
                                border = null
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                Divider(color = Color.White.copy(alpha = 0.05f), thickness = 1.dp)
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = { navController.navigate(Screen.Library.createRoute(viewModel.serverId)) },
                    containerColor = PrimaryDark,
                    contentColor = BackgroundDark,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("INSTALL", fontWeight = FontWeight.Black) }
                )
            }
        }
    ) { padding ->
        if (selectedTab == 2) {
             Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                 Text("Server Logs (Coming Soon)", color = Color.White.copy(0.3f))
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
                        text = if (searchQuery.isNotBlank()) "No results for '$searchQuery'" else "Nothing here yet",
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (item.isEnabled) Color.White else Color.White.copy(alpha = 0.3f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = "by ${item.author} • v${item.version}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.4f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            
            Spacer(Modifier.width(8.dp))

            // Toggle & Delete
            if (item.type != "world") {
                Switch(
                    checked = item.isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = PrimaryDark,
                        checkedThumbColor = BackgroundDark,
                        uncheckedTrackColor = Color.White.copy(0.1f),
                        uncheckedThumbColor = Color.White.copy(0.4f),
                        uncheckedBorderColor = Color.Transparent
                    )
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove",
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
