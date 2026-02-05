package com.lzofseven.mcserver.ui.screens.config

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lzofseven.mcserver.core.auth.ApiKeyManager
import com.lzofseven.mcserver.ui.theme.BackgroundDark
import com.lzofseven.mcserver.ui.theme.PrimaryDark
import com.lzofseven.mcserver.ui.theme.SurfaceDark
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeyConfigScreen(
    navController: NavController,
    viewModel: ApiKeyConfigViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apiKey by remember { mutableStateOf("") }
    var isVisible by remember { mutableStateOf(false) }
    
    val savedKey by viewModel.apiKey.collectAsState()
    
    // Load saved key
    LaunchedEffect(savedKey) {
        if (!savedKey.isNullOrBlank()) apiKey = savedKey
    }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = { Text("Configurar IA (Gemini)", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Text(
                    "Para usar o Builder Bot, você precisa de uma Chave de API Gratuita do Google Gemini.",
                    color = Color.White.copy(0.7f),
                    fontSize = 14.sp
                )
            }

            // Tutorial Cards
            item {
                TutorialStepCard(
                    step = "1",
                    title = "Acesse o Google AI Studio",
                    description = "Toque abaixo para abrir o site no navegador. Faça login com sua conta Google.",
                    actionLabel = "Abrir AI Studio",
                    actionIcon = Icons.Default.OpenInNew,
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                        context.startActivity(intent)
                    }
                )
            }

            item {
                TutorialStepCard(
                    step = "2",
                    title = "Crie sua Chave de API",
                    description = "Dentro do site, clique no botão azul \"Create API key\". Se for solicitado, selecione um projeto ou crie um novo."
                )
            }

            item {
                TutorialStepCard(
                    step = "3",
                    title = "Copie o Código",
                    description = "Procure pela chave que começa com \"AIza...\". Clique no ícone de cópia ao lado dela para salvar no seu teclado."
                )
            }

            item {
                TutorialStepCard(
                    step = "4",
                    title = "Cole e Salve",
                    description = "Volte para esta tela e cole o código no campo abaixo. Clique em \"SALVAR\" para ativar a inteligência."
                )
            }

            item {
                Surface(
                    color = Color.Yellow.copy(0.1f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Yellow.copy(0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Dica: Se você receber erro de 'Região não suportada', use uma VPN ligada nos EUA ou use uma conta Google que não tenha restrições de país.",
                        modifier = Modifier.padding(12.dp),
                        color = Color.Yellow.copy(0.9f),
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            // Input Field
            item {
                Surface(
                    color = SurfaceDark,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Sua Chave API", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isVisible = !isVisible }) {
                                    Icon(
                                        if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        null,
                                        tint = Color.White.copy(0.5f)
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryDark,
                                unfocusedBorderColor = Color.White.copy(0.2f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            placeholder = { Text("AIzaSy...", color = Color.White.copy(0.3f)) }
                        )
                        
                        Spacer(Modifier.height(16.dp))
                        
                        Button(
                            onClick = {
                                viewModel.saveApiKey(apiKey.trim())
                                navController.popBackStack()
                            },
                            enabled = apiKey.isNotBlank() && apiKey.startsWith("AIza"),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryDark),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Check, null)
                            Spacer(Modifier.width(8.dp))
                            Text("SALVAR E ATIVAR BOT", fontWeight = FontWeight.Bold, color = BackgroundDark)
                        }
                        
                         if (!savedKey.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            TextButton(
                                onClick = { 
                                    viewModel.clearApiKey()
                                    apiKey = ""
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Remover Chave Salva", color = Color.Red.copy(0.7f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TutorialStepCard(
    step: String,
    title: String,
    description: String,
    actionLabel: String? = null,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: (() -> Unit)? = null,
    imageRes: Int? = null
) {
    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.05f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Step Number
            Surface(
                color = PrimaryDark.copy(0.1f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(step, color = PrimaryDark, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text(description, color = Color.White.copy(0.6f), fontSize = 14.sp, lineHeight = 20.sp)
                
                if (actionLabel != null && onClick != null) {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.1f)),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        if (actionIcon != null) {
                            Icon(actionIcon, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(actionLabel, color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
