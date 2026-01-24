package com.lzofseven.mcserver.ui.screens.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.lzofseven.mcserver.ui.theme.BackgroundDark
import com.lzofseven.mcserver.ui.theme.PrimaryDark
import com.lzofseven.mcserver.ui.theme.SurfaceDark
import com.lzofseven.mcserver.core.ai.ChatMessage
import com.lzofseven.mcserver.core.ai.ActionStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    navController: NavController,
    viewModel: AiChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val listState = rememberLazyListState()

    // Auto-scroll to bottom
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        containerColor = BackgroundDark,
        contentWindowInsets = WindowInsets(0, 0, 0, 0), // Disable default insets to handle them manually
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, null, tint = PrimaryDark)
                        Spacer(Modifier.width(8.dp))
                        Text("Builder Bot", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .navigationBarsPadding() // Keep input above nav bar
                .imePadding() // Keep input above keyboard
        ) {
            // Chat Area
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        EmptyState()
                    }
                }
                
                items(messages) { msg ->
                    var visible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { visible = true }
                    
                    androidx.compose.animation.AnimatedVisibility(
                        visible = visible,
                        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(initialOffsetY = { it / 2 })
                    ) {
                        ChatBubble(message = msg)
                    }
                }
                
                if (isLoading) {
                    item {
                        LoadingBubble()
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BackgroundDark)
                    .padding(bottom = 8.dp)
            ) {
                val needsRconSetup by viewModel.needsRconSetup.collectAsState()
                var pendingText by remember { mutableStateOf("") }
                val suggestions by viewModel.suggestions.collectAsState()
                
                if (needsRconSetup) {
                    RconRequiredCard(
                        onSetupClick = { viewModel.setupRcon() },
                        isLoading = isLoading
                    )
                } else {
                    Column {
                        // Quick Suggestions - Professional Bank
                        QuickSuggestions(
                            suggestions = suggestions,
                            onSuggestionClick = { pendingText = it }
                        )
                        
                        ChatInput(
                            onSend = { viewModel.sendMessage(it) },
                            isLoading = isLoading,
                            externalText = pendingText,
                            onTextConsumed = { pendingText = "" }
                        )
                    }
                }
            }
        }

        if (errorMessage != null) {
            AlertDialog(
                onDismissRequest = { viewModel.clearError() },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("OK", color = PrimaryDark)
                    }
                },
                title = { Text("Erro", color = Color.White) },
                text = { Text(errorMessage ?: "Erro desconhecido", color = Color.White.copy(0.7f)) },
                containerColor = SurfaceDark
            )
        }
    }
}

@Composable
fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.SmartToy,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.2f),
            modifier = Modifier.size(80.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Oi! Sou o Builder Bot.\nPosso ajudar a construir estruturas,\ngerenciar o servidor e muito mais.",
            color = Color.White.copy(alpha = 0.4f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 24.sp
        )
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    val isSystem = message.role == "system" // For execution logs
    
    val alignment = when {
        isUser -> Alignment.CenterEnd
        isSystem -> Alignment.Center
        else -> Alignment.CenterStart
    }
    
    val containerColor = when {
        isUser -> PrimaryDark
        isSystem -> Color.Transparent // System messages are specialized
        else -> SurfaceDark
    }

    val textColor = if (isUser) BackgroundDark else Color.White

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        if (message.isOrchestrationLog) {
            ToolExecutionCard(message.content)
        } else if (isSystem) {
            // Traditional log message
            Text(
                text = message.content,
                style = MaterialTheme.typography.labelSmall,
                color = PrimaryDark.copy(alpha = 0.8f),
                modifier = Modifier
                    .background(PrimaryDark.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        } else {
            Column(
                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
            ) {
                if (!isUser) {
                    Text("Builder Bot", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 4.dp, start = 4.dp))
                }
                
                Surface(
                    color = containerColor,
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    ),
                    modifier = Modifier.widthIn(min = 40.dp, max = 320.dp)
                ) {
                    // Logic to handle "Action blocks"
                    val parsedContent = remember(message.content, message.actionStatuses) { 
                        parseMessageContent(message.content, message.actionStatuses) 
                    }
                    
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (parsedContent.text.isNotBlank()) {
                            if (!isUser && !message.isOrchestrationLog) {
                                TypewriterText(
                                    text = parsedContent.text,
                                    color = textColor
                                )
                            } else {
                                MarkdownText(
                                    text = parsedContent.text,
                                    color = textColor
                                )
                            }
                        }
                        
                        // If there are actions, separate them with a spacer if text exists
                        parsedContent.actions.forEach { action ->
                            if (parsedContent.text.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                            }
                            ActionCard(action)
                        }
                    }
                }
            }
        }
    }
}

data class ParsedUiContent(
    val text: String,
    val actions: List<AiActionUi>
)

data class AiActionUi(
    val type: String,
    val content: String,
    val status: ActionStatus = ActionStatus.PENDING
)

@Composable
fun ActionCard(action: AiActionUi) {
    // Parse type and args
    val parts = action.type.split(":")
    val rawType = parts[0]
    val arg = parts.getOrNull(1)?.replace("_", " ")?.replaceFirstChar { it.uppercase() }

    val title = when(rawType) {
        "execute" -> "Executar Comando"
        "mcfunction" -> if (arg != null) "Construção: $arg" else "Gerar Função"
        else -> rawType.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
    }
    
    val icon = when(rawType) {
        "execute" -> Icons.Filled.Terminal
        "mcfunction" -> Icons.Filled.Build
        else -> Icons.Filled.Code
    }

    var expanded by remember { mutableStateOf(false) }
    val lineCount = action.content.lines().size
    val previewLines = 4

    Surface(
        color = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.1f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = PrimaryDark.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                         Icon(imageVector = icon, contentDescription = null, tint = PrimaryDark, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    if (rawType == "mcfunction") {
                         Text("$lineCount comandos", color = Color.White.copy(0.5f), style = MaterialTheme.typography.labelSmall)
                    }
                }
                
                Spacer(Modifier.weight(1f))
                
                // Status Indicator
                when (action.status) {
                    ActionStatus.EXECUTING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = PrimaryDark
                        )
                    }
                    ActionStatus.SUCCESS -> {
                        Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                    }
                    ActionStatus.ERROR -> {
                        Icon(Icons.Filled.Error, null, tint = Color(0xFFEF5350), modifier = Modifier.size(18.dp))
                    }
                    else -> {}
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Code Content
            Surface(
                color = Color.Black.copy(0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    val displayContent = if (expanded || lineCount <= previewLines) action.content else action.content.lines().take(previewLines).joinToString("\n")
                    
                    Text(
                        text = displayContent, 
                        color = Color(0xFFAAAAAA), 
                        style = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 11.sp),
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                    
                    if (lineCount > previewLines) {
                        Surface(
                            color = Color.White.copy(0.05f),
                            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
                        ) {
                            Text(
                                if (expanded) "Recolher" else "Ver mais ${lineCount - previewLines} linhas...",
                                color = PrimaryDark,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

// ...

fun parseMessageContent(rawContent: String, statuses: Map<Int, ActionStatus>): ParsedUiContent {
    // Regex now allows metadata after type: ```type:metadata\n
    val regex = Regex("```([^\\n]+)\\n([\\s\\S]*?)```")
    val actions = mutableListOf<AiActionUi>()
    var lastIndex = 0
    val textBuilder = StringBuilder()
    var actionIndex = 0

    regex.findAll(rawContent).forEach { matchResult ->
        // Text before the block
        if (matchResult.range.first > lastIndex) {
            textBuilder.append(rawContent.substring(lastIndex, matchResult.range.first))
        }
        
        val type = matchResult.groupValues[1].trim()
        val content = matchResult.groupValues[2].trim()
        val status = statuses[actionIndex] ?: ActionStatus.PENDING
        actions.add(AiActionUi(type, content, status))
        
        lastIndex = matchResult.range.last + 1
        actionIndex++
    }

    // Remaining text
    if (lastIndex < rawContent.length) {
        textBuilder.append(rawContent.substring(lastIndex))
    }

    return ParsedUiContent(textBuilder.toString().trim(), actions)
}

@Composable
fun MarkdownText(text: String, color: Color, modifier: Modifier = Modifier) {
    val annotatedString = remember(text) {
        parseMarkdown(text)
    }
    Text(
        text = annotatedString,
        color = color,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
fun TypewriterText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    delayMillis: Long = 30
) {
    var textToDisplay by remember { mutableStateOf("") }
    
    LaunchedEffect(text) {
        textToDisplay = ""
        text.forEach { char ->
            textToDisplay += char
            kotlinx.coroutines.delay(delayMillis)
        }
    }

    MarkdownText(
        text = textToDisplay,
        color = color,
        modifier = modifier
    )
}

fun parseMarkdown(text: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        var currentIndex = 0
        // Simple parser for **bold** and *italic*
        // Regex is a bit complex for full markdown, but for this specific request:
        // ((\*\*(.*?)\*\*)|(\*(.*?)\*))
        
        val regex = Regex("(\\*\\*(.*?)\\*\\*)|(\n)|(\\* (.*?)\\n)") // Bold, newlines, and list items (simple)
        // Wait, list items are tricky without block logic. Let's stick to bold/italic inline.
        
        // Better regex for Bold (**...**) and Italic (*...*)
        val mdRegex = Regex("(\\*\\*(.*?)\\*\\*)|(\\*(.*?)\\*)")
        
        var lastIndex = 0
        mdRegex.findAll(text).forEach { matchResult ->
            val range = matchResult.range
            // Append plain text before match
            if (range.first > lastIndex) {
                 append(text.substring(lastIndex, range.first))
            }
             
            // Handle match
            val fullMatch = matchResult.value
             if (fullMatch.startsWith("**")) {
                 val content = matchResult.groupValues[2]
                 withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                     append(content)
                 }
             } else if (fullMatch.startsWith("*")) {
                 val content = matchResult.groupValues[4]
                 withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                     append(content)
                 }
             }
             
             lastIndex = range.last + 1
        }
        
        // Append remaining text
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
}

@Composable
fun QuickSuggestions(
    suggestions: List<com.lzofseven.mcserver.core.ai.AiSuggestion>,
    onSuggestionClick: (String) -> Unit
) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(suggestions) { suggestion ->
            Surface(
                color = SurfaceDark,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                modifier = Modifier.clickable { onSuggestionClick(suggestion.detailedPrompt) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = suggestion.icon,
                        contentDescription = null,
                        tint = PrimaryDark,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = suggestion.label,
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
fun LoadingBubble() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Surface(
            color = SurfaceDark,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = PrimaryDark
                )
                Spacer(Modifier.width(8.dp))
                Text("Pensando...", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun ChatInput(onSend: (String) -> Unit, isLoading: Boolean, externalText: String = "", onTextConsumed: () -> Unit = {}) {
    var text by remember { mutableStateOf("") }
    
    // Sync with external suggestions
    LaunchedEffect(externalText) {
        if (externalText.isNotEmpty()) {
            text = externalText
            onTextConsumed()
        }
    }

    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(32.dp),
        shadowElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.1f)),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp, max = 200.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .wrapContentHeight(),
            verticalAlignment = Alignment.Bottom // Align button to bottom as it expands
        ) {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .weight(1f)
                    .background(Color.Transparent),
                placeholder = { Text("Pergunte ao Builder Bot...", color = Color.White.copy(alpha = 0.3f), fontSize = 14.sp) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = PrimaryDark
                ),
                singleLine = false,
                maxLines = 6
            )
            
            IconButton(
                onClick = {
                    if (text.isNotBlank()) {
                        onSend(text)
                        text = ""
                    }
                },
                enabled = !isLoading && text.isNotBlank(),
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (!isLoading && text.isNotBlank()) PrimaryDark else Color.White.copy(alpha = 0.05f),
                        CircleShape
                    )
            ) {
                Icon(
                    Icons.Default.Send,
                    null,
                    tint = if (!isLoading && text.isNotBlank()) BackgroundDark else Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun RconRequiredCard(onSetupClick: () -> Unit, isLoading: Boolean) {
    Surface(
        color = Color.Red.copy(alpha = 0.1f),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red.copy(alpha = 0.2f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Error, 
                null, 
                tint = Color.Red, 
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "RCON REQUERIDO", 
                style = MaterialTheme.typography.titleMedium, 
                fontWeight = FontWeight.Black, 
                color = Color.White
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "A IA precisa do protocolo RCON ativo para enviar comandos ao servidor Minecraft.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            
            Button(
                onClick = onSetupClick,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("CONFIGURAR AUTOMATICAMENTE", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun ToolExecutionCard(rawContent: String) {
    // Parse content: "🛠 Executando tool_name: {args}" OR "Log message"
    val isTool = rawContent.contains("Executando")
    val toolData = if (isTool) parseToolLogData(rawContent) else null
    
    val containerColor = if (isTool) Color(0xFF1E1E1E) else Color.Transparent
    val borderColor = if (isTool) PrimaryDark.copy(alpha = 0.3f) else PrimaryDark.copy(alpha = 0.1f)
    
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (isTool && toolData != null) {
                // Header: Icon + Tool Name
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when(toolData.name) {
                            "write_file" -> Icons.Default.Build
                            "read_file", "list_files" -> Icons.Default.Terminal
                            "run_command" -> Icons.Default.Terminal
                            "get_logs" -> Icons.Default.Error
                            else -> Icons.Default.Code
                        },
                        contentDescription = null,
                        tint = PrimaryDark,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = toolData.name.replace("_", " ").uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryDark
                    )
                }
                
                Spacer(Modifier.height(8.dp))
                
                // Arguments List
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    toolData.args.forEach { (key, value) ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = "$key:", 
                                style = MaterialTheme.typography.bodySmall, 
                                color = Color.White.copy(alpha = 0.5f),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 10.sp,
                                modifier = Modifier.width(60.dp)
                            )
                            Text(
                                text = value, 
                                style = MaterialTheme.typography.bodySmall, 
                                color = Color.White.copy(alpha = 0.8f),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            } else {
                // Generic Log (Planner/Debugger thoughts)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (rawContent.contains("Arquiteto")) Icons.Default.AutoAwesome else Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = rawContent,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        fontStyle = FontStyle.Italic
                    )
                }
            }
        }
    }
}

data class ToolInfo(val name: String, val args: Map<String, String>)

fun parseToolLogData(log: String): ToolInfo? {
    try {
        // Format: "🛠 Executando tool_name: {arg1=val1, arg2=val2}"
        val nameStart = log.indexOf("Executando ") + 11
        val argsStart = log.indexOf(": {")
        
        if (nameStart < 11 || argsStart == -1) return null
        
        val name = log.substring(nameStart, argsStart).trim()
        val argsRaw = log.substring(argsStart + 3, log.lastIndexOf("}"))
        
        // Simple manual csv parsing
        val args = mutableMapOf<String, String>()
        val parts = argsRaw.split(", ")
        for (part in parts) {
            val kv = part.split("=")
            if (kv.size >= 2) {
                args[kv[0]] = kv.drop(1).joinToString("=")
            }
        }
        return ToolInfo(name, args)
    } catch (e: Exception) {
        return null
    }
}
