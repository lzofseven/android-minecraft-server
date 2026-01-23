package com.lzofseven.mcserver.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object McVersionUtils {

    /**
     * Map Minecraft versions to required Java versions.
     * Logic:
     * 1.20.5+ -> Java 21
     * 1.18 - 1.20.4 -> Java 17
     * 1.17 -> Java 16
     * 1.16.5 and below -> Java 8
     */
    fun getRequiredJavaVersion(mcVersion: String): Int {
        val v = parseVersion(mcVersion)
        return when {
            v >= Version(1, 20, 5) -> 21
            v >= Version(1, 18, 0) -> 17
            v >= Version(1, 17, 0) -> 16
            else -> 8
        }
    }

    fun getSupportedVersions(type: String): List<String> {
        val majorVersions = listOf(
            "1.21.1", "1.21", 
            "1.20.6", "1.20.4", "1.20.2", "1.20.1", "1.20",
            "1.19.4", "1.19.3", "1.19.2", "1.19.1", "1.19",
            "1.18.2", "1.18.1", "1.18",
            "1.17.1", "1.17",
            "1.16.5", "1.16.4", "1.16.3", "1.16.2", "1.16.1", "1.16",
            "1.15.2", "1.15.1", "1.15",
            "1.14.4", "1.14.3", "1.14.2", "1.14.1", "1.14",
            "1.13.2", "1.13.1", "1.13",
            "1.12.2", "1.12.1", "1.12",
            "1.11.2", "1.11.1", "1.11",
            "1.10.2", "1.10.1", "1.10",
            "1.9.4", "1.9.2", "1.9",
            "1.8.9", "1.8.8", "1.8",
            "1.7.10", "1.7.2"
        )

        return when (type.lowercase()) {
            "vanilla", "paper", "fabric" -> majorVersions
            "forge" -> listOf(
                "1.20.6", "1.20.4", "1.20.2", "1.20.1", "1.20",
                "1.19.4", "1.19.3", "1.19.2", "1.19.1", "1.19",
                "1.18.2", "1.18.1", "1.18",
                "1.17.1",
                "1.16.5", "1.16.4", "1.16.3", "1.16.2", "1.16.1",
                "1.15.2",
                "1.14.4",
                "1.13.2",
                "1.12.2",
                "1.11.2",
                "1.10.2",
                "1.9.4",
                "1.8.9",
                "1.7.10"
            )
            "neoforge" -> listOf("1.21.1", "1.21", "1.20.6", "1.20.4", "1.20.2", "1.20.1")
            "pocketmine" -> listOf("Latest")
            "bedrock" -> listOf("1.21.60.04", "1.21.50.29", "1.21.40.01", "1.21.30.03") // Hardcoded recent stable BDS for reference
            else -> emptyList()
        }
    }

    suspend fun getDownloadUrl(type: String, version: String): String = withContext(Dispatchers.IO) {
        when (type.lowercase()) {
            "vanilla" -> getVanillaUrl(version)
            "paper" -> getPaperUrl(version)
            "fabric" -> getFabricUrl(version)
            "forge" -> getForgeUrl(version)
            "neoforge" -> getNeoForgeUrl(version)
            else -> throw IllegalArgumentException("Unsupported server type: $type")
        }
    }

    private fun getVanillaUrl(version: String): String {
        try {
            val manifestJson = URL("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json").readText()
            val manifest = JSONObject(manifestJson)
            val versions = manifest.getJSONArray("versions")

            var versionUrl: String? = null
            for (i in 0 until versions.length()) {
                val entry = versions.getJSONObject(i)
                if (entry.getString("id") == version) {
                    versionUrl = entry.getString("url")
                    break
                }
            }

            if (versionUrl == null) {
                throw IllegalArgumentException("Version $version not found in Mojang manifest.")
            }

            val versionDataJson = URL(versionUrl).readText()
            val versionData = JSONObject(versionDataJson)
            val downloads = versionData.getJSONObject("downloads")
            
            if (!downloads.has("server")) {
                throw IllegalArgumentException("No server download available for version $version")
            }
            
            return downloads.getJSONObject("server").getString("url")

        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception("Failed to fetch Vanilla URL for $version: ${e.message}")
        }
    }

    private fun getPaperUrl(originalVersion: String): String {
        // Map 1.8.9 to 1.8.8 for Paper as 1.8.9 doesn't exist in Paper
        val version = if (originalVersion == "1.8.9") "1.8.8" else originalVersion

        try {
            // 1. Check if version exists
            val versionUrl = "https://api.papermc.io/v2/projects/paper/versions/$version"
            val versionJsonData = try {
                 URL(versionUrl).readText()
            } catch (e: Exception) {
                throw IllegalArgumentException("Paper version $version not found (Mapped from $originalVersion). Status: 404/Error")
            }
            
            val versionJson = JSONObject(versionJsonData)
            val builds = versionJson.getJSONArray("builds")
            
            if (builds.length() == 0) throw Exception("No builds found for Paper $version")
            
            val latestBuild = builds.getInt(builds.length() - 1)
            
            return "https://api.papermc.io/v2/projects/paper/versions/$version/builds/$latestBuild/downloads/paper-$version-$latestBuild.jar"

        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception("Failed to fetch Paper URL for $version: ${e.message}")
        }
    }

    private fun getFabricUrl(version: String): String {
        try {
            // Fetch compatible loader
            val loaderUrl = "https://meta.fabricmc.net/v2/versions/loader/$version"
            val loaderJsonData = URL(loaderUrl).readText()
            val loaderJson = org.json.JSONArray(loaderJsonData)
            
            if (loaderJson.length() == 0) throw Exception("No Fabric loader found for $version")
            
            val latestLoader = loaderJson.getJSONObject(0).getJSONObject("loader").getString("version")
            val installerVersion = "1.0.0" // Standard server installer wrapper
            
            // Fabric Meta v2 Server JAR endpoint
            // /v2/versions/loader/:game_version/:loader_version/:installer_version/server/jar
            return "https://meta.fabricmc.net/v2/versions/loader/$version/$latestLoader/$installerVersion/server/jar"
            
        } catch (e: Exception) {
             e.printStackTrace()
             throw Exception("Failed to fetch Fabric URL for $version: ${e.message}")
        }
    }

    private fun getForgeUrl(version: String): String {
        // Forge logic: Get promotions_slim.json -> find recommended/latest -> construct Maven URL
        try {
            val jsonUrl = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json"
            val jsonData = URL(jsonUrl).readText()
            val json = JSONObject(jsonData)
            val promos = json.getJSONObject("promos")
            
            // Try recommended first, then latest
            val versionKeyRecommended = "$version-recommended"
            val versionKeyLatest = "$version-latest"
            
            val forgeVersion = when {
                promos.has(versionKeyRecommended) -> promos.getString(versionKeyRecommended)
                promos.has(versionKeyLatest) -> promos.getString(versionKeyLatest)
                else -> throw Exception("No Forge build found for $version (checked recommended and latest)")
            }
            
            // Construct Maven URL
            // Format: https://maven.minecraftforge.net/net/minecraftforge/forge/{mcVer}-{forgeVer}/forge-{mcVer}-{forgeVer}-installer.jar
            // Note: Older versions (1.7.10 - 1.8.9) might use slightly different formats or universal jars.
            // For now, widespread standard is -installer.jar
            
            return "https://maven.minecraftforge.net/net/minecraftforge/forge/$version-$forgeVersion/forge-$version-$forgeVersion-installer.jar"

        } catch (e: Exception) {
             e.printStackTrace()
             throw Exception("Failed to fetch Forge URL for $version: ${e.message}")
        }
    }
    
    private fun getNeoForgeUrl(version: String): String {
        try {
            // NeoForge Maven Metadata
            val metaUrl = "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml"
            val metaData = URL(metaUrl).readText()
            
            // Simple XML parsing to find all <version> tags
            val versions = Regex("<version>(.*?)</version>").findAll(metaData)
                .map { it.groupValues[1] }
                .toList()
            
            // Find the version that matches the MC version
            // NeoForge naming convention varies:
            // 20.2.x-beta -> for 1.20.2
            // 20.4.x -> for 1.20.4
            // 21.x.x -> for 1.21
            // Sometimes it has the full MC version prefix.
            
            // Heuristic:
            // 1. Look for exact match (rare for NeoForge standalone)
            // 2. Look for "MCVersion-NeoVersion"
            // 3. Look for "Major.Minor.Patch" where Major corresponds to 1.X
            
            var targetVersion: String? = null
            
            // Handle new versioning scheme (e.g. 21.0.0-beta for 1.21)
            // 1.20.1 -> 47.1.x (Wait, that's Forge history. NeoForge started at 1.20.1)
            // Official NeoForge First Version: 1.20.1
            
            // Helper to extract MC major/minor from 1.20.4 -> 20.4
            val parts = version.split(".")
            if (parts.size < 2) throw Exception("Invalid version format: $version")
            
            val mcMajor = parts[1] // 20 from 1.20
            val mcMinor = if (parts.size > 2) parts[2] else "0" // 4 from 1.20.4
            
            val searchPrefix = "$mcMajor.$mcMinor" // e.g. "20.4"
            
            // Filter versions starting with searchPrefix OR containing the full version
            // Examples in repo: 20.4.80-beta, 21.0.106-beta
            
            val candidates = versions.filter { 
                it.startsWith(searchPrefix) || it.startsWith(version) 
            }.sortedWith(Comparator { o1, o2 -> 
                // specialized compare to pick latest (simplistic)
                o1.compareTo(o2)
            })
            
            if (candidates.isEmpty()) {
                 throw Exception("No NeoForge version found for Minecraft $version")
            }
            
            targetVersion = candidates.last() // Take the "largest" / latest string match
            
            // URL Format: https://maven.neoforged.net/releases/net/neoforged/neoforge/<version>/neoforge-<version>-installer.jar
            return "https://maven.neoforged.net/releases/net/neoforged/neoforge/$targetVersion/neoforge-$targetVersion-installer.jar"

        } catch (e: Exception) {
             e.printStackTrace()
             throw Exception("Failed to fetch NeoForge URL for $version: ${e.message}")
        }
    }
    

    

    
    // -- Version Comparison Logic --
    
    data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {
        override fun compareTo(other: Version): Int {
            if (major != other.major) return major - other.major
            if (minor != other.minor) return minor - other.minor
            return patch - other.patch
        }
    }

    private fun parseVersion(v: String): Version {
        val parts = v.split(".").mapNotNull { it.toIntOrNull() }
        return Version(
            parts.getOrElse(0) { 0 },
            parts.getOrElse(1) { 0 },
            parts.getOrElse(2) { 0 }
        )
    }
}
