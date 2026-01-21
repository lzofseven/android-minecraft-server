package com.lzofseven.mcserver.util

object McVersionUtils {

    /**
     * Map Minecraft versions to required Java versions (8, 16, 17, 21).
     */
    fun getRequiredJavaVersion(mcVersion: String): Int {
        val version = mcVersion.lowercase()
        return when {
            version.startsWith("1.8") || version.startsWith("1.9") || 
            version.startsWith("1.10") || version.startsWith("1.11") || 
            version.startsWith("1.12") || version.startsWith("1.13") ||
            version.startsWith("1.14") || version.startsWith("1.15") ||
            version.startsWith("1.16") -> 8
            
            version.startsWith("1.17") -> 16
            
            version.startsWith("1.18") || version.startsWith("1.19") || 
            version == "1.20" || version == "1.20.1" || version == "1.20.2" || version == "1.20.4" -> 17
            
            else -> 21 // 1.20.5+ and 1.21+
        }
    }

    /**
     * Determine the appropriate PaperMC download URL.
     */
    fun getPaperDownloadUrl(mcVersion: String): String {
        return when (mcVersion) {
            "1.21.1" -> "https://api.papermc.io/v2/projects/paper/versions/1.21.1/builds/126/downloads/paper-1.21.1-126.jar"
            "1.20.4" -> "https://api.papermc.io/v2/projects/paper/versions/1.20.4/builds/496/downloads/paper-1.20.4-496.jar"
            "1.20.1" -> "https://api.papermc.io/v2/projects/paper/versions/1.20.1/builds/196/downloads/paper-1.20.1-196.jar"
            "1.19.4" -> "https://api.papermc.io/v2/projects/paper/versions/1.19.4/builds/550/downloads/paper-1.19.4-550.jar"
            "1.18.2" -> "https://api.papermc.io/v2/projects/paper/versions/1.18.2/builds/388/downloads/paper-1.18.2-388.jar"
            "1.17.1" -> "https://api.papermc.io/v2/projects/paper/versions/1.17.1/builds/411/downloads/paper-1.17.1-411.jar"
            "1.16.5" -> "https://api.papermc.io/v2/projects/paper/versions/1.16.5/builds/794/downloads/paper-1.16.5-794.jar"
            "1.12.2" -> "https://api.papermc.io/v2/projects/paper/versions/1.12.2/builds/1618/downloads/paper-1.12.2-1618.jar"
            "1.8.9"  -> "https://api.papermc.io/v2/projects/paper/versions/1.8.8/builds/445/downloads/paper-1.8.8-445.jar"
            else -> "https://api.papermc.io/v2/projects/paper/versions/1.20.1/builds/196/downloads/paper-1.20.1-196.jar"
        }
    }
}
