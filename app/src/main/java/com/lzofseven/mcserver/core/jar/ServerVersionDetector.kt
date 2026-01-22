package com.lzofseven.mcserver.core.jar

import android.content.Context
import android.util.Log
import com.lzofseven.mcserver.core.metadata.ServerMetadata
import java.io.File
import java.util.jar.JarFile

/**
 * Detects Minecraft server version from various sources
 */
class ServerVersionDetector {
    
    data class DetectedVersion(
        val version: String,      // e.g., "1.8.9", "1.20.1"
        val type: ServerType,
        val confidence: Float     // 0.0 to 1.0
    )
    
    enum class ServerType {
        VANILLA,
        PAPER,
        SPIGOT,
        BUKKIT,
        FORGE,
        FABRIC,
        UNKNOWN
    }
    
    companion object {
        private const val TAG = "VersionDetector"
        private val VERSION_REGEX = Regex("""(\d+\.\d+(?:\.\d+)?)""")
    }
    
    /**
     * Detect version using multiple strategies
     * Returns null if version cannot be determined (NO FALLBACK)
     */
    fun detectVersion(
        context: Context,
        serverPath: String,
        serverName: String,
        serverVersion: String? = null  // Version from database (user-selected)
    ): DetectedVersion? {
        Log.d(TAG, "Detecting version for: $serverName (DB version: $serverVersion)")
        
        // Strategy 0: Read metadata file (ABSOLUTE HIGHEST PRIORITY - source of truth)
        ServerMetadata.load(context, serverPath)?.let { metadata ->
            Log.i(TAG, "✅ Using metadata file version: ${metadata.version}")
            return DetectedVersion(metadata.version, ServerType.PAPER, 1.0f)
        }
        
        // Strategy 1: Use database version if available
        serverVersion?.let { version ->
            if (isValidVersion(version)) {
                Log.i(TAG, "✅ Using database version: $version")
                return DetectedVersion(version, ServerType.PAPER, 1.0f)
            }
        }
        
        // Strategy 2: Parse from server name
        parseVersionFromName(serverName)?.let { detected ->
            Log.i(TAG, "Detected from name: ${detected.version} (${detected.type})")
            return detected
        }
        
        // Strategy 3: Parse from server directory path
        parseVersionFromName(serverPath)?.let { detected ->
            Log.i(TAG, "Detected from path: ${detected.version} (${detected.type})")
            return detected
        }
        
        // Strategy 4: Read server.properties (direct file path only)
        if (!serverPath.startsWith("content://")) {
            readServerProperties(File(serverPath))?.let { version ->
                Log.i(TAG, "Detected from server.properties: $version")
                return DetectedVersion(version, ServerType.VANILLA, 0.9f)
            }
        }
        
        // Strategy 5: Try to read JAR manifest (direct file path only)
        if (!serverPath.startsWith("content://")) {
            val jarFile = File(serverPath, "server.jar")
            if (jarFile.exists() && jarFile.length() > 1_000_000) {
                readJarManifest(jarFile)?.let { detected ->
                    Log.i(TAG, "Detected from JAR manifest: ${detected.version}")
                    return detected
                }
            }
        }
        
        // NO FALLBACK - return null to signal failure
        Log.e(TAG, "❌ Could not detect version from any source - NO FALLBACK")
        return null
    }
    
    /**
     * Parse version from server name or path
     */
    private fun parseVersionFromName(name: String): DetectedVersion? {
        // Common patterns:
        // "server-1.8.9", "minecraft 1.16.5", "Paper-1.20.1", "teste 1.21"
        
        val lowerName = name.lowercase()
        
        // Detect server type from name
        val type = when {
            "paper" in lowerName -> ServerType.PAPER
            "spigot" in lowerName -> ServerType.SPIGOT
            "bukkit" in lowerName -> ServerType.BUKKIT
            "forge" in lowerName -> ServerType.FORGE
            "fabric" in lowerName -> ServerType.FABRIC
            else -> ServerType.VANILLA
        }
        
        // Extract version number
        VERSION_REGEX.find(name)?.let { match ->
            val version = match.groupValues[1]
            
            // Validate version format
            if (isValidVersion(version)) {
                return DetectedVersion(version, type, 0.8f)
            }
        }
        
        return null
    }
    
    /**
     * Read version from server.properties
     * (Created after first run with generator-settings or level-seed)
     */
    private fun readServerProperties(serverDir: File): String? {
        val propsFile = File(serverDir, "server.properties")
        if (!propsFile.exists()) return null
        
        try {
            propsFile.readLines().forEach { line ->
                // Look for version hints in comments or properties
                if (line.contains("Minecraft", ignoreCase = true)) {
                    VERSION_REGEX.find(line)?.let { match ->
                        return match.groupValues[1]
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error reading server.properties", e)
        }
        
        return null
    }
    
    /**
     * Read version from JAR manifest
     */
    private fun readJarManifest(jarFile: File): DetectedVersion? {
        try {
            JarFile(jarFile).use { jar ->
                val manifest = jar.manifest ?: return null
                val attrs = manifest.mainAttributes
                
                // Check common manifest attributes
                val version = attrs.getValue("Implementation-Version")
                    ?: attrs.getValue("Specification-Version")
                    ?: attrs.getValue("Minecraft-Version")
                
                version?.let {
                    VERSION_REGEX.find(it)?.let { match ->
                        val versionNum = match.groupValues[1]
                        
                        // Detect type from implementation title
                        val implTitle = attrs.getValue("Implementation-Title") ?: ""
                        val type = when {
                            "paper" in implTitle.lowercase() -> ServerType.PAPER
                            "spigot" in implTitle.lowercase() -> ServerType.SPIGOT
                            "craftbukkit" in implTitle.lowercase() -> ServerType.BUKKIT
                            else -> ServerType.VANILLA
                        }
                        
                        return DetectedVersion(versionNum, type, 1.0f)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error reading JAR manifest", e)
        }
        
        return null
    }
    
    /**
     * Validate version number format
     */
    private fun isValidVersion(version: String): Boolean {
        val parts = version.split(".")
        return parts.size >= 2 && parts.all { it.toIntOrNull() != null }
    }
}
