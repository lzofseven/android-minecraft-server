package com.lzofseven.mcserver.core.jar

import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * Validates Minecraft server JAR files for integrity and correctness
 */
class JarValidator {
    
    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
        data class Corrupted(val reason: String) : ValidationResult()
    }
    
    companion object {
        private const val TAG = "JarValidator"
        private const val MIN_JAR_SIZE = 50_000L // 50KB minimum (Fabric installers are small)
    }

    /**
     * Validate JAR using DocumentFile (SAF)
     */
    fun validateJar(docFile: DocumentFile?): ValidationResult {
        if (docFile == null || !docFile.exists()) {
            return ValidationResult.Invalid("File does not exist via SAF")
        }
        
        val size = docFile.length()
        if (size < MIN_JAR_SIZE) {
            return ValidationResult.Invalid("File too small via SAF (${size / 1024}KB)")
        }
        
        // For SAF, full zip validation is complex (requires copying to temp)
        // We'll trust the size/existence for now.
        return ValidationResult.Valid
    }
    
    /**
     * Comprehensive JAR validation
     */
    fun validateJar(jarFile: File): ValidationResult {
        Log.d(TAG, "Validating JAR: ${jarFile.absolutePath}")
        
        // 1. Check file exists
        if (!jarFile.exists()) {
            return ValidationResult.Invalid("File does not exist")
        }
        
        // 2. Check minimum size
        val size = jarFile.length()
        if (size < MIN_JAR_SIZE) {
            return ValidationResult.Invalid("File too small (${size / 1024}KB, expected >50KB)")
        }
        
        // 3. Try to open as ZIP
        try {
            ZipFile(jarFile).use { zip ->
                // 4. Check for META-INF/MANIFEST.MF
                val manifest = zip.getEntry("META-INF/MANIFEST.MF")
                if (manifest == null) {
                    return ValidationResult.Invalid("No MANIFEST.MF found")
                }
                
                // 5. Look for server main class indicators
                var hasServerClasses = false
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    
                    // Check for common server class names
                    if (name.contains("MinecraftServer", ignoreCase = true) ||
                        name.contains("DedicatedServer", ignoreCase = true) ||
                        name.contains("Main.class") ||
                        name.contains("net/minecraft/server/") ||
                        name.contains("net/fabricmc/")) {
                        hasServerClasses = true
                        break
                    }
                }
                
                if (!hasServerClasses) {
                    return ValidationResult.Invalid("Doesn't appear to be a Minecraft server JAR")
                }
                
                Log.d(TAG, "JAR appears valid (${size / 1_000_000}MB)")
                return ValidationResult.Valid
            }
        } catch (e: ZipException) {
            Log.e(TAG, "ZIP corruption detected", e)
            return ValidationResult.Corrupted("ZIP file corrupted: ${e.message}")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security error reading JAR", e)
            return ValidationResult.Invalid("Cannot read JAR (permissions): ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error validating JAR", e)
            return ValidationResult.Corrupted("Cannot read JAR: ${e.message}")
        }
    }
    
    /**
     * Quick size check without opening the file
     */
    fun quickValidate(jarFile: File): Boolean {
        return jarFile.exists() && jarFile.length() > MIN_JAR_SIZE
    }
}
