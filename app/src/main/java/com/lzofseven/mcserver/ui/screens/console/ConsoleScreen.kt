package com.lzofseven.mcserver.ui.screens.console

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.lzofseven.mcserver.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleScreen(
    navController: NavController,
    viewModel: ConsoleViewModel = hiltViewModel()
) {
    val logs by viewModel.logs.collectAsState()
    var commandInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }
    
    Box(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { 
                        Column {
                            Text("Console do Servidor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White)
                            Text("Logs em tempo real", style = MaterialTheme.typography.labelSmall, color = PrimaryDark, fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Voltar", tint = Color.White.copy(alpha = 0.6f))
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.clearLogs() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Limpar", tint = ErrorDark)
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
                    .padding(horizontal = 16.dp)
            ) {
                // Terminal Surface
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(logs) { log ->
                            Row {
                                Text(
                                    text = log,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = when {
                                        log.contains("ERROR", ignoreCase = true) -> ErrorDark
                                        log.contains("WARN", ignoreCase = true) -> TertiaryDark
                                        log.contains("INFO", ignoreCase = true) -> PrimaryDark
                                        else -> Color.White.copy(alpha = 0.7f)
                                    }
                                )
                            }
                        }
                    }
                }
                
                // Floating command bar
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                        .height(64.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = commandInput,
                            onValueChange = { commandInput = it },
                            placeholder = { Text("Digite um comando...", color = Color.White.copy(alpha = 0.3f), fontSize = 13.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = PrimaryDark,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                        )
                        
                        IconButton(
                            onClick = {
                                if (commandInput.isNotBlank()) {
                                    viewModel.sendCommand(commandInput)
                                    commandInput = ""
                                }
                            },
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(PrimaryDark)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Enviar", tint = BackgroundDark, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}
