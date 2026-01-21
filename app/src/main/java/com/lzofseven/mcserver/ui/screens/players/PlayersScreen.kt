package com.lzofseven.mcserver.ui.screens.players

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lzofseven.mcserver.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayersScreen(
    navController: NavController,
    viewModel: PlayersViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableStateOf(0) }
    val ops by viewModel.ops.collectAsState()
    val whitelist by viewModel.whitelist.collectAsState()
    val onlinePlayers by viewModel.onlinePlayers.collectAsState(initial = emptyList())
    val filteredPlayers by viewModel.filteredPlayers.collectAsState()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var newPlayerName by remember { mutableStateOf("") }
    
    // Whisper State
    var whisperTarget by remember { mutableStateOf<String?>(null) }
    var whisperMessage by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        // ... (Scaffold structure omitted for brevity, keeping context)
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { 
                        Column {
                            Text("Jogadores Online", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color.White)
                            val onlinePlayersCount = onlinePlayers.size
                            val gameMode by viewModel.gameMode.collectAsState()
                            Text("$onlinePlayersCount Conectados • ${gameMode.replaceFirstChar { it.titlecase() }}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
                        }
                    },
                    // ... (nav icon and actions)
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Voltar", tint = Color.White.copy(alpha = 0.6f))
                        }
                    },
                    actions = {
                        var isSearchVisible by remember { mutableStateOf(false) }
                        val searchQuery by viewModel.searchQuery.collectAsState()
                        
                        if (isSearchVisible) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { viewModel.onSearchQueryChange(it) },
                                modifier = Modifier.width(180.dp),
                                placeholder = { Text("Buscar...", color = Color.White.copy(0.4f)) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = PrimaryDark,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    cursorColor = PrimaryDark,
                                    focusedTextColor = Color.White
                                ),
                                trailingIcon = {
                                    IconButton(onClick = { 
                                        isSearchVisible = false
                                        viewModel.onSearchQueryChange("")
                                    }) { Icon(Icons.Default.Close, null, tint = Color.White) }
                                }
                            )
                        } else {
                            IconButton(onClick = { isSearchVisible = true }) { Icon(Icons.Default.Search, null, tint = Color.White) }
                        }
                        
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMenu = true }) { 
                            Icon(Icons.Default.FilterList, null, tint = Color.White) 
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(SurfaceDark)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Operadores", color = Color.White) },
                                    onClick = { selectedTab = 1; showMenu = false },
                                    leadingIcon = { Icon(Icons.Default.Gavel, null, tint = PrimaryDark) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Whitelist", color = Color.White) },
                                    onClick = { selectedTab = 2; showMenu = false },
                                    leadingIcon = { Icon(Icons.Default.List, null, tint = PrimaryDark) }
                                )
                                Divider(color = Color.White.copy(alpha = 0.05f))
                                DropdownMenuItem(
                                    text = { Text("Online", color = Color.White) },
                                    onClick = { selectedTab = 0; showMenu = false },
                                    leadingIcon = { Icon(Icons.Default.Person, null, tint = PrimaryDark) }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = PrimaryDark,
                    contentColor = BackgroundDark,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Adicionar")
                }
            }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                
                // Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = PrimaryDark,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = PrimaryDark,
                            height = 3.dp
                        )
                    },
                    divider = { Divider(color = Color.White.copy(alpha = 0.05f)) }
                ) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("ONLINE (${onlinePlayers.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("OPERADORES", fontWeight = FontWeight.Bold, fontSize = 12.sp) })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("WHITELIST", fontWeight = FontWeight.Bold, fontSize = 12.sp) })
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                     if (selectedTab == 0) {
                         if (filteredPlayers.isEmpty()) {
                             item {
                                 Text("Nenhum jogador encontrado.", color = Color.White.copy(0.5f), modifier = Modifier.padding(16.dp))
                             }
                         } else {
                             items(filteredPlayers) { player ->
                                 PlayerItem(
                                     name = player, 
                                     isOnline = true,
                                     isAdmin = false, 
                                     onRemove = { /* Kick logic todo */ },
                                     onChatClick = { whisperTarget = player }
                                 )
                             }
                         }
                     } else if (selectedTab == 1) {
                        items(ops) { player ->
                            PlayerItem(
                                name = player.name, 
                                isOnline = onlinePlayers.contains(player.name),
                                isAdmin = true, 
                                onRemove = { viewModel.removeOp(player) },
                                onChatClick = { whisperTarget = player.name }
                            )
                        }
                    } else if (selectedTab == 2) {
                        items(whitelist) { player ->
                            PlayerItem(
                                name = player.name, 
                                isOnline = onlinePlayers.contains(player.name),
                                isAdmin = false, 
                                onRemove = { viewModel.removeWhitelist(player) },
                                onChatClick = { whisperTarget = player.name }
                            )
                        }
                    }
                }
            }

            if (showAddDialog) {
                AddPlayerDialog(
                    selectedTab = selectedTab,
                    newPlayerName = newPlayerName,
                    onNameChange = { newPlayerName = it },
                    onConfirm = {
                        if (newPlayerName.isNotBlank()) {
                            if (selectedTab == 1) viewModel.addOp(newPlayerName)
                            else if (selectedTab == 2) viewModel.addWhitelist(newPlayerName)
                            newPlayerName = ""
                            showAddDialog = false
                        }
                    },
                    onDismiss = { showAddDialog = false }
                )
            }
            
            if (whisperTarget != null) {
                WhisperDialog(
                    targetPlayer = whisperTarget!!,
                    message = whisperMessage,
                    onMessageChange = { whisperMessage = it },
                    onSend = {
                        viewModel.sendWhisper(whisperTarget!!, whisperMessage)
                        whisperMessage = ""
                        whisperTarget = null
                    },
                    onDismiss = { 
                        whisperTarget = null 
                        whisperMessage = ""
                    }
                )
            }
        }
    }
}

@Composable
fun RealTimeIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(PrimaryDark))
            Spacer(Modifier.width(8.dp))
            Text("SERVIDOR EM TEMPO REAL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.4f), letterSpacing = 2.sp)
        }
        Text("ATUALIZAR", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = PrimaryDark, letterSpacing = 1.sp)
    }
}

@Composable
fun PlayerItem(name: String, isOnline: Boolean, isAdmin: Boolean, onRemove: () -> Unit, onChatClick: () -> Unit) {
    Surface(
        color = Color.White.copy(alpha = 0.03f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Minecraft Avatar with real head API
                Box(modifier = Modifier.size(52.dp)) {
                    Surface(
                        color = Color.White.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        AsyncImage(
                            model = "https://mc-heads.net/avatar/$name",
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                        )
                    }
                    Box(modifier = Modifier.size(12.dp).background(if (isOnline) PrimaryDark else Color.Gray, CircleShape).border(2.dp, BackgroundDark, CircleShape).align(Alignment.BottomEnd))
                }
                
                Spacer(Modifier.width(16.dp))
                
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, fontWeight = FontWeight.Black, color = Color.White, fontSize = 16.sp)
                        if (isAdmin) {
                            Spacer(Modifier.width(8.dp))
                            Surface(color = PrimaryDark.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, PrimaryDark.copy(alpha = 0.2f))) {
                                Text("ADMIN", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = PrimaryDark, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    Text(if (isOnline) "On-line" else "Off-line", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
                }
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onChatClick, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.05f))) {
                    Icon(Icons.Default.ChatBubble, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(ErrorDark.copy(alpha = 0.1f))) {
                    Icon(Icons.Default.Gavel, null, tint = ErrorDark, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun AddPlayerDialog(
    selectedTab: Int,
    newPlayerName: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (selectedTab == 1) "Adicionar Operador" else "Adicionar na Whitelist", color = Color.White, fontWeight = FontWeight.Black) },
        text = {
            OutlinedTextField(
                value = newPlayerName,
                onValueChange = onNameChange,
                label = { Text("Nickname do Jogador") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryDark,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                    focusedLabelColor = PrimaryDark,
                    unfocusedLabelColor = Color.White.copy(alpha = 0.4f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
        },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = PrimaryDark)) {
                Text("ADICIONAR", color = BackgroundDark, fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCELAR", color = Color.White.copy(alpha = 0.4f))
            }
        },
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun WhisperDialog(
    targetPlayer: String,
    message: String,
    onMessageChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Conversar com $targetPlayer", color = Color.White, fontWeight = FontWeight.Black) },
        text = {
            OutlinedTextField(
                value = message,
                onValueChange = onMessageChange,
                label = { Text("Sussurrar (/tell)") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryDark,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                    focusedLabelColor = PrimaryDark,
                    unfocusedLabelColor = Color.White.copy(alpha = 0.4f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
        },
        confirmButton = {
            Button(onClick = onSend, colors = ButtonDefaults.buttonColors(containerColor = PrimaryDark)) {
                Text("ENVIAR", color = BackgroundDark, fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCELAR", color = Color.White.copy(alpha = 0.4f))
            }
        },
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(24.dp)
    )
}
