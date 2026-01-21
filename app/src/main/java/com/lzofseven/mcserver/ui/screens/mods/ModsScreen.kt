package com.lzofseven.mcserver.ui.screens.mods

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.lzofseven.mcserver.ui.navigation.Screen
import com.lzofseven.mcserver.ui.theme.BackgroundDark
import com.lzofseven.mcserver.ui.theme.PrimaryDark
import com.lzofseven.mcserver.ui.theme.SurfaceDark
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModsScreen(
    navController: NavController,
    viewModel: ModsViewModel = hiltViewModel()
) {
    val content by viewModel.installedContent.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val currentPath by viewModel.currentBrowserPath.collectAsState()
    val browserFiles by viewModel.browserFiles.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState("")
    
    val snackbarHostState = remember { SnackbarHostState() }
    val tabs = listOf("Mods", "Plugins", "Mundos", "Arquivos")
    
    var searchQuery by remember { mutableStateOf("") }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var editingFile by remember { mutableStateOf<File?>(null) }
    var fileContent by remember { mutableStateOf("") }

    LaunchedEffect(toastMessage) {
        if (toastMessage.isNotEmpty()) {
            snackbarHostState.showSnackbar(toastMessage)
        }
    }

    Scaffold(
        containerColor = BackgroundDark,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(modifier = Modifier.background(BackgroundDark).padding(top = 16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Voltar", tint = Color.White)
                    }
                    Text(
                        "Gerenciar Conteúdo",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Tabs
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

                // Search or Navigation
                if (selectedTab < 3) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f).height(52.dp),
                            placeholder = { Text("Buscar...", color = Color.White.copy(0.3f), fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(20.dp)) },
                            shape = RoundedCornerShape(12.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.White.copy(0.15f),
                                unfocusedBorderColor = Color.White.copy(0.1f),
                                unfocusedContainerColor = Color.White.copy(0.05f),
                                focusedContainerColor = Color.White.copy(0.08f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true
                        )
                        
                        Surface(
                            onClick = { navController.navigate(Screen.Library.createRoute(viewModel.serverId)) },
                            color = PrimaryDark,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Add, "Adicionar", tint = BackgroundDark)
                            }
                        }
                    }
                } else {
                    // Browser Navigator
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = { viewModel.navigateBack() }) {
                            Icon(Icons.Default.ArrowUpward, "Subir", tint = PrimaryDark)
                        }
                        Surface(
                            color = Color.White.copy(0.05f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(horizontal = 12.dp)) {
                                Text(
                                    text = currentPath?.absolutePath?.substringAfter(viewModel.serverId) ?: "/",
                                    color = Color.White.copy(0.5f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        IconButton(onClick = { showCreateFolderDialog = true }) {
                            Icon(Icons.Default.CreateNewFolder, "Nova Pasta", tint = Color.White.copy(0.6f))
                        }
                        IconButton(onClick = { viewModel.pasteFile() }) {
                            Icon(Icons.Default.ContentPaste, "Colar", tint = Color.White.copy(0.6f))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Divider(color = Color.White.copy(alpha = 0.05f), thickness = 1.dp)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (selectedTab < 3) {
                val filteredContent = content.filter { it.name.contains(searchQuery, ignoreCase = true) }
                if (filteredContent.isEmpty()) {
                    EmptyState(if (selectedTab == 0) "Nenhum mod instalado" else if (selectedTab == 1) "Nenhum plugin instalado" else "Nenhum mundo encontrado")
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredContent) { item ->
                            ContentCard(item = item, onToggle = { viewModel.toggleItem(it) }, onDelete = { viewModel.deleteItem(it) })
                        }
                    }
                }
            } else {
                if (browserFiles.isEmpty()) {
                    EmptyState("Pasta vazia")
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(browserFiles) { file ->
                            FileItem(
                                file = file,
                                onNavigate = { viewModel.navigateTo(it) },
                                onDelete = { viewModel.deleteFile(it) },
                                onCopy = { viewModel.copyFile(it) },
                                onEdit = {
                                    editingFile = file
                                    fileContent = viewModel.readFile(file)
                                },
                                onRename = { name -> viewModel.renameFile(file, name) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateFolderDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Nova Pasta", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome da pasta") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.createFolder(name)
                    showCreateFolderDialog = false
                }) { Text("Criar") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancelar") }
            },
            containerColor = SurfaceDark
        )
    }

    if (editingFile != null) {
        AlertDialog(
            onDismissRequest = { editingFile = null },
            title = { Text(editingFile?.name ?: "Editar", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = fileContent,
                    onValueChange = { fileContent = it },
                    modifier = Modifier.fillMaxWidth().height(400.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
            },
            confirmButton = {
                Button(onClick = {
                    editingFile?.let { viewModel.saveFile(it, fileContent) }
                    editingFile = null
                }) { Text("Salvar") }
            },
            dismissButton = {
                TextButton(onClick = { editingFile = null }) { Text("Sair") }
            },
            containerColor = SurfaceDark
        )
    }
}

@Composable
fun EmptyState(text: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Inbox, null, tint = Color.White.copy(0.1f), modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(text, color = Color.White.copy(0.3f), fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ContentCard(item: InstalledContent, onToggle: (InstalledContent) -> Unit, onDelete: (InstalledContent) -> Unit) {
    Surface(
        color = Color.White.copy(alpha = 0.03f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = if(item.isEnabled) PrimaryDark.copy(0.1f) else Color.White.copy(0.05f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (item.type == "world") Icons.Default.Public else Icons.Default.Extension,
                        null,
                        tint = if(item.isEnabled) PrimaryDark else Color.White.copy(0.3f)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Versão ${item.version} • Por ${item.author}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.4f))
            }
            
            if (item.type != "world") {
                CustomSwitch(
                    checked = item.isEnabled,
                    onCheckedChange = { onToggle(item) }
                )
            }
            
            IconButton(onClick = { onDelete(item) }) {
                Icon(Icons.Default.Delete, null, tint = Color.Red.copy(0.6f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun FileItem(
    file: File, 
    onNavigate: (File) -> Unit, 
    onDelete: (File) -> Unit, 
    onCopy: (File) -> Unit, 
    onEdit: (File) -> Unit,
    onRename: (String) -> Unit
) {
    val isEditable = file.extension in listOf("txt", "yml", "properties", "json", "conf", "sh", "bat", "py", "js")
    var showRenameDialog by remember { mutableStateOf(false) }

    Surface(
        onClick = { if (file.isDirectory) onNavigate(file) else if (isEditable) onEdit(file) },
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                null,
                tint = if (file.isDirectory) Color(0xFFFFCC80) else Color.White.copy(0.4f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(file.name, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (file.isFile) {
                    Text("${file.length() / 1024} KB", color = Color.White.copy(0.3f), style = MaterialTheme.typography.labelSmall)
                }
            }
            
            Row {
                if (file.isFile && isEditable) {
                    IconButton(onClick = { onEdit(file) }) {
                        Icon(Icons.Default.Edit, null, tint = Color.White.copy(0.3f), modifier = Modifier.size(18.dp))
                    }
                }
                IconButton(onClick = { showRenameDialog = true }) {
                    Icon(Icons.Default.DriveFileRenameOutline, null, tint = Color.White.copy(0.3f), modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { onCopy(file) }) {
                    Icon(Icons.Default.ContentCopy, null, tint = Color.White.copy(0.3f), modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { onDelete(file) }) {
                    Icon(Icons.Default.Delete, null, tint = Color.Red.copy(0.4f), modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    if (showRenameDialog) {
        var name by remember { mutableStateOf(file.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Renomear", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    onRename(name)
                    showRenameDialog = false
                }) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancelar") }
            },
            containerColor = SurfaceDark
        )
    }
}

@Composable
fun CustomSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Box(modifier = Modifier.graphicsLayer(scaleX = 0.7f, scaleY = 0.7f)) {
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = PrimaryDark,
                checkedThumbColor = Color.Black
            )
        )
    }
}
