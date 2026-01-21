
package com.lzofseven.mcserver

import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

class VersionVerificationTest {

    // Minimal copy of McVersionUtils logic for standalone testing
    object VersionTester {
        fun getDownloadUrl(type: String, version: String): String {
            return when (type.lowercase()) {
                "vanilla" -> getVanillaUrl(version)
                "paper" -> getPaperUrl(version)
                "fabric" -> getFabricUrl(version)
                "forge" -> getForgeUrl(version)
                "neoforge" -> getNeoForgeUrl(version)
                else -> throw IllegalArgumentException("Unsupported: $type")
            }
        }

        private fun getVanillaUrl(version: String): String {
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
            if (versionUrl == null) throw Exception("Vanilla $version not found")
            val versionData = JSONObject(URL(versionUrl).readText())
            return versionData.getJSONObject("downloads").getJSONObject("server").getString("url")
        }

        private fun getPaperUrl(originalVersion: String): String {
            val version = if (originalVersion == "1.8.9") "1.8.8" else originalVersion
            val versionUrl = "https://api.papermc.io/v2/projects/paper/versions/$version"
            
            // Handle 404 gracefully? No, let it throw for now
            val versionJson = JSONObject(URL(versionUrl).readText())
            val builds = versionJson.getJSONArray("builds")
            val latestBuild = builds.getInt(builds.length() - 1)
            return "https://api.papermc.io/v2/projects/paper/versions/$version/builds/$latestBuild/downloads/paper-$version-$latestBuild.jar"
        }

        private fun getFabricUrl(version: String): String {
            val loaderUrl = "https://meta.fabricmc.net/v2/versions/loader/$version"
            val loaderJson = org.json.JSONArray(URL(loaderUrl).readText())
            if (loaderJson.length() == 0) throw Exception("No Fabric loader for $version")
            val latestLoader = loaderJson.getJSONObject(0).getJSONObject("loader").getString("version")
            return "https://meta.fabricmc.net/v2/versions/loader/$version/$latestLoader/1.0.0/server/jar"
        }

        private fun getForgeUrl(version: String): String {
            val jsonUrl = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json"
            val promos = JSONObject(URL(jsonUrl).readText()).getJSONObject("promos")
            val forgeVersion = when {
                promos.has("$version-recommended") -> promos.getString("$version-recommended")
                promos.has("$version-latest") -> promos.getString("$version-latest")
                else -> throw Exception("No Forge build for $version")
            }
            return "https://maven.minecraftforge.net/net/minecraftforge/forge/$version-$forgeVersion/forge-$version-$forgeVersion-installer.jar"
        }

        private fun getNeoForgeUrl(version: String): String {
            val metaUrl = "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml"
            val metaData = URL(metaUrl).readText()
            val versions = Regex("<version>(.*?)</version>").findAll(metaData).map { it.groupValues[1] }.toList()
            
            val parts = version.split(".")
            if (parts.size < 2) throw Exception("Invalid version: $version")
            val searchPrefix = "${parts[1]}.${if (parts.size > 2) parts[2] else "0"}"
            
            val candidates = versions.filter { it.startsWith(searchPrefix) || it.startsWith(version) }.sorted()
            if (candidates.isEmpty()) throw Exception("No NeoForge found for $version")
            
            val targetVersion = candidates.last()
            return "https://maven.neoforged.net/releases/net/neoforged/neoforge/$targetVersion/neoforge-$targetVersion-installer.jar"
        }
    }

    @Test
    fun verifyAllVersions() {
        // Full list from 1.8.9 to 1.21.11
        val versions = listOf(
            "1.8.9",
            "1.9", "1.9.1", "1.9.2", "1.9.3", "1.9.4",
            "1.10", "1.10.1", "1.10.2",
            "1.11", "1.11.1", "1.11.2",
            "1.12", "1.12.1", "1.12.2",
            "1.13", "1.13.1", "1.13.2",
            "1.14", "1.14.1", "1.14.2", "1.14.3", "1.14.4",
            "1.15", "1.15.1", "1.15.2",
            "1.16", "1.16.1", "1.16.2", "1.16.3", "1.16.4", "1.16.5",
            "1.17", "1.17.1",
            "1.18", "1.18.1", "1.18.2",
            "1.19", "1.19.1", "1.19.2", "1.19.3", "1.19.4",
            "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6",
            "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4",
            "1.21.5", "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10", "1.21.11"
        )
        val types = listOf("vanilla", "paper", "fabric", "forge", "neoforge")

        println("=== STARTING VERSION VERIFICATION ===")
        
        for (type in types) {
            println("\nTesting Type: ${type.uppercase()}")
            for (version in versions) {
                // Skip combinations that don't exist
                if (type == "forge" && version == "1.21") {
                    // Check if 1.21 forge exists or not. It likely does by now.
                }

                try {
                    print("  Verification for $version... ")
                    val urlStr = VersionTester.getDownloadUrl(type, version)
                    
                    // Validate URL availability (HEAD request)
                    val connection = URL(urlStr).openConnection() as HttpURLConnection
                    connection.requestMethod = "HEAD"
                    connection.connectTimeout = 5000
                    connection.connect()
                    
                    val code = connection.responseCode
                    if (code == 200 || code == 302) {
                        println("✅ OK ($code)")
                        // println("     -> $urlStr")
                    } else {
                        println("❌ FAIL ($code)")
                        println("     -> $urlStr")
                    }
                } catch (e: Exception) {
                    println("⚠️ ERROR: ${e.message}")
                }
            }
        }
        println("\n=== VERIFICATION COMPLETE ===")
    }
}
