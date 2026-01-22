package com.lzofseven.mcserver.core.java

import android.content.Context
import com.lzofseven.mcserver.util.DownloadStatus
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.ar.ArArchiveEntry
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream
import kotlinx.coroutines.delay
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JavaVersionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient
) {

    private val runtimesDir = File(context.filesDir, "runtimes")
    private val cacheDir = File(context.cacheDir, "java_installer_cache")

    init {
        if (!runtimesDir.exists()) runtimesDir.mkdirs()
        if (!cacheDir.exists()) cacheDir.mkdirs()
    }

    fun getJavaExecutable(version: Int): File {
        // Map to available runtimes: Java 17 for everything up to v17, Java 21 for v21+
        val targetVersion = if (version <= 17) 17 else 21
        return File(runtimesDir, "java-$targetVersion/bin/java")
    }

    fun isJavaInstalled(version: Int): Boolean {
        val javaBin = getJavaExecutable(version)
        val installDir = javaBin.parentFile.parentFile
        val libz = File(installDir, "lib/libz.so.1")
        // Termux java binaries are small launchers (~6KB), so we can't check for >1MB.
        // Also check libz.so.1 to ensure symlink-related corruption is addressed.
        return javaBin.exists() && javaBin.canExecute() && javaBin.length() > 0 && 
               (!libz.exists() || libz.length() > 0) // if exists, must not be 0 bytes
    }

    fun installJava(version: Int): Flow<DownloadStatus> = flow {
        // Map to available runtimes: Java 17 for everything up to v17, Java 21 for v21+
        val targetVersion = if (version <= 17) 17 else 21
        val installDir = File(runtimesDir, "java-$targetVersion")
        
        if (isJavaInstalled(targetVersion)) {
            emit(DownloadStatus.Finished(installDir))
            return@flow
        }
        
        // Clean up partial/broken install
        if (installDir.exists()) {
            installDir.deleteRecursively()
        }
        installDir.mkdirs()

        emit(DownloadStatus.Started)

        try {
            // 1. Extract Dependencies from Assets
            extractAssetLib("zlib_1.3.1-1_aarch64.deb", installDir)
            extractAssetLib("libandroid-shmem_0.7_aarch64.deb", installDir)
            extractAssetLib("libandroid-spawn_0.3_aarch64.deb", installDir)

            // 2. Extract JDK from Assets (only Java 17 available)
            val jdkAssetName = "openjdk-17_17.0.17-1_aarch64.deb"
            
            val jdkDeb = File(cacheDir, "jdk-$targetVersion.deb")
            emit(DownloadStatus.Progress(10)) 

            // Copy JDK deb from assets to cache
            copyAssetToCache(jdkAssetName, jdkDeb).collect { status ->
                 if (status is DownloadStatus.Progress) {
                     emit(DownloadStatus.Progress(10 + (status.percentage * 0.4).toInt())) 
                 }
            }

            // 3. Extract JDK
            emit(DownloadStatus.Progress(50))
            extractDeb(jdkDeb, installDir)
            
            // 4. Setup Permissions & Symlinks
            setupRuntime(installDir)
            
            emit(DownloadStatus.Finished(installDir))
            
            // Cleanup cache
            jdkDeb.delete()

        } catch (e: Exception) {
            Log.e("JavaVersionManager", "Erro na instalação do Java: ${e.message}", e)
            emit(DownloadStatus.Error("Falha na instalação interna: ${e.toString()}"))
            installDir.deleteRecursively() // Cleanup on failure
        }
    }.flowOn(Dispatchers.IO)


    private fun extractAssetLib(assetName: String, installDir: File) {
        val debFile = File(cacheDir, assetName)
        if (debFile.exists()) debFile.delete()
        
        context.assets.open("runtimes/$assetName").use { input ->
            FileOutputStream(debFile).use { output ->
                input.copyTo(output)
            }
        }
        extractDeb(debFile, installDir)
        debFile.delete()
    }

    private fun copyAssetToCache(assetName: String, target: File): Flow<DownloadStatus> = flow {
        Log.d("JavaVersionManager", "Extraindo asset: $assetName")
        
        // Try to get length for progress (might not work for compressed assets)
        val totalBytes = try {
            context.assets.openFd("runtimes/$assetName").length
        } catch (e: Exception) { -1L }

        context.assets.open("runtimes/$assetName").use { input ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(32768)
                var bytesRead = 0L
                var read: Int
                
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    bytesRead += read
                    if (totalBytes > 0) {
                        emit(DownloadStatus.Progress(((bytesRead * 100) / totalBytes).toInt()))
                    }
                }
            }
        }
        Log.d("JavaVersionManager", "Extração concluída: ${target.name}")
    }

    private fun extractDeb(debFile: File, targetDir: File) {
        val arInput = ArArchiveInputStream(FileInputStream(debFile))
        var entry: ArArchiveEntry?
        
        while (arInput.nextArEntry.also { entry = it } != null) {
            if (entry!!.name == "data.tar.xz") {
                val xzInput = XZInputStream(arInput)
                val tarInput = TarArchiveInputStream(xzInput)
                
                var tarEntry = tarInput.nextTarEntry
                while (tarEntry != null) {
                    val path = tarEntry.name
                    if (tarEntry.isDirectory) {
                        tarEntry = tarInput.nextTarEntry
                        continue
                    }

                    val destFile = resolveDestination(path, targetDir)
                    if (destFile != null) {
                        destFile.parentFile?.mkdirs()
                        
                        if (tarEntry.isSymbolicLink || tarEntry.isLink) {
                            val linkTarget = tarEntry.linkName
                            handleLink(destFile, linkTarget, targetDir)
                        } else {
                            if (destFile.exists()) destFile.delete()
                            FileOutputStream(destFile).use { fos ->
                                val buffer = ByteArray(8192)
                                var len: Int
                                while (tarInput.read(buffer).also { len = it } != -1) {
                                    fos.write(buffer, 0, len)
                                }
                            }
                            if (path.contains("/bin/")) {
                                destFile.setExecutable(true)
                            }
                        }
                    }
                    
                    tarEntry = tarInput.nextTarEntry
                }
                break // Found data.tar.xz, done
            }
        }
        arInput.close()
    }

    private fun handleLink(destFile: File, linkTarget: String, targetDir: File) {
        try {
            if (destFile.exists()) destFile.delete()
            
            // If the link target is an absolute path from the package (starts with /), 
            // we should try to map it to our targetDir.
            // But usually it's relative like "libz.so.1.3.1"
            
            var mappedTarget = linkTarget
            if (linkTarget.startsWith("/")) {
                val resolved = resolveDestination(linkTarget, targetDir)
                if (resolved != null) {
                    mappedTarget = resolved.absolutePath
                }
            } else {
                // For relative links in our flattened lib structure, 
                // if it's "libz.so.1.3.1" and we are in "lib/libz.so.1", 
                // it just works if both are in lib/.
            }

            android.system.Os.symlink(mappedTarget, destFile.absolutePath)
            Log.d("JavaVersionManager", "Created symlink: ${destFile.name} -> $mappedTarget")
        } catch (e: Exception) {
            Log.w("JavaVersionManager", "Failed to create symlink ${destFile.name}, attempting copy if target exists", e)
            // Fallback: if it's a relative link in the same dir, try to copy
            try {
                val targetFile = if (linkTarget.startsWith("/")) File(linkTarget) else File(destFile.parentFile, linkTarget)
                if (targetFile.exists() && targetFile.isFile) {
                    targetFile.copyTo(destFile, overwrite = true)
                    Log.d("JavaVersionManager", "Copied target instead of symlink for ${destFile.name}")
                }
            } catch (e2: Exception) {
                Log.e("JavaVersionManager", "Critical failure handling link ${destFile.name}", e2)
            }
        }
    }
    
    // Smart Flattening Logic
    private fun resolveDestination(path: String, installDir: File): File? {
        // Detect JDK content
        if (path.contains("/usr/lib/jvm/")) {
            // Format: .../jvm/java-17-openjdk/bin/java -> installDir/bin/java
            val relativePath = path.substringAfter("/jvm/").substringAfter("/") 
            // Needs to handle the java-17-openjdk folder name dynamically or ignore it
            return File(installDir, relativePath)
        }
        
        // Detect Shared Libs (zlib, etc)
        // Format: .../usr/lib/libz.so -> installDir/lib/libz.so
        if (path.contains("/usr/lib/") && !path.contains("/jvm/")) {
            val libName = path.substringAfterLast("/")
            return File(installDir, "lib/$libName")
        }
        
        return null // Skip man pages, docs, etc
    }

    private fun setupRuntime(installDir: File) {
        val binDir = File(installDir, "bin")
        binDir.listFiles()?.forEach { it.setExecutable(true) }
        
        val libDir = File(installDir, "lib")
        if (!libDir.exists()) libDir.mkdirs()

        // 1. Create libc++_shared.so symlink to system libc++.so
        // This is required because Termux binaries are linked against libc++_shared.so
        val libCppShared = File(libDir, "libc++_shared.so")
        if (!libCppShared.exists()) {
            try {
                val systemLibCpp = if (File("/system/lib64/libc++.so").exists()) {
                    "/system/lib64/libc++.so"
                } else {
                    "/system/lib/libc++.so"
                }
                android.system.Os.symlink(systemLibCpp, libCppShared.absolutePath)
                Log.d("JavaVersionManager", "Created libc++_shared.so symlink -> $systemLibCpp")
            } catch (e: Exception) {
                Log.e("JavaVersionManager", "Failed to create libc++ symlink", e)
            }
        }

        // 2. Compatibility Symlink logic for versioned libs (Ex: zlib.so.1 -> zlib.so)
        libDir.listFiles()?.forEach { file ->
            if (file.name.contains(".so.") && !file.name.endsWith(".so")) {
                val baseName = file.name.substringBefore(".so") + ".so"
                val linkFile = File(libDir, baseName)
                if (!linkFile.exists()) {
                    try {
                        android.system.Os.symlink(file.name, linkFile.absolutePath)
                        Log.d("JavaVersionManager", "Created versioned symlink: ${linkFile.name} -> ${file.name}")
                    } catch (e: Exception) {
                        // ignore 
                    }
                }
            }
        }
    }
}
