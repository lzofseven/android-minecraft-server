package com.lzofseven.mcserver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.lzofseven.mcserver.ui.navigation.NavGraph
import com.lzofseven.mcserver.ui.theme.AndroidMinecraftServerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var repository: com.lzofseven.mcserver.data.repository.ServerRepository
    @Inject lateinit var serverManager: com.lzofseven.mcserver.core.execution.RealServerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // AutoStart Logic
        lifecycleScope.launch {
            try {
                val servers = repository.getAllServersList()
                servers.forEach { server ->
                    if (server.autoStart) {
                        try {
                            android.util.Log.i("MainActivity", "Auto-starting server: ${server.name}")
                            serverManager.startServer(server)
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Failed to auto-start ${server.name}", e)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error in auto-start loop", e)
            }
        }

        enableEdgeToEdge()
        setContent {
            AndroidMinecraftServerTheme {
                val context = androidx.compose.ui.platform.LocalContext.current
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        try {
                            if (androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.POST_NOTIFICATIONS
                                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                // We can't request directly from LaunchedEffect without Activity/Launcher
                                // But since this is MainActivity, we can use Accompanist or just standard ActivityResultLauncher
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                
                // Permission Launcher
                val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                    onResult = { isGranted ->
                        // Handle result if needed
                    }
                )

                androidx.compose.runtime.LaunchedEffect(Unit) {
                     if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                         if (androidx.core.content.ContextCompat.checkSelfPermission(
                                 context, 
                                 android.Manifest.permission.POST_NOTIFICATIONS
                             ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                         ) {
                             permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                         }
                     }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0) // Key fix: don't let root scaffold consume insets
                ) { innerPadding ->
                    NavGraph(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}
