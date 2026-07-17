package com.motrix.download.engine

import android.content.Context
import android.os.Build
import android.util.Log
import com.motrix.download.util.Constants
import java.io.*
import java.util.concurrent.TimeUnit

class Aria2Engine(private val context: Context) {
    private var process: Process? = null
    @Volatile
    private var running = false
    private val startLock = Any()

    private val filesDir: File
        get() = context.filesDir

    private val sessionFile: File
        get() = File(filesDir, "download.session")

    private val configFile: File
        get() = File(filesDir, "aria2.conf")

    private val dhtFile: File
        get() = File(filesDir, "dht.dat")

    private val dht6File: File
        get() = File(filesDir, "dht6.dat")

    fun start(options: Map<String, String> = emptyMap()): Boolean {
        synchronized(startLock) {
            Log.i(TAG, "╔══════════════════════════════════════════════╗")
            Log.i(TAG, "║     Aria2Engine.start() v3 — nativeLibDir    ║")
            Log.i(TAG, "╚══════════════════════════════════════════════╝")

            if (running && process?.isAlive == true) {
                Log.i(TAG, "Already running, skipping")
                return true
            }

            // Check if an existing aria2c instance is already running on our port
            if (tryConnectToPort()) {
                Log.i(TAG, "aria2c already running on port ${Constants.ENGINE_RPC_PORT}, reusing")
                running = true
                return true
            }

            // Kill any orphaned aria2c processes from previous sessions
            killOrphanedProcesses()

            try {
                // ═══ STEP 1: Device info ═══
            Log.i(TAG, "── STEP 1: Device info ──")
            Log.i(TAG, "  SUPPORTED_ABIS  = ${Build.SUPPORTED_ABIS.joinToString(", ")}")
            Log.i(TAG, "  CPU_ABI         = ${Build.CPU_ABI}")
            Log.i(TAG, "  os.arch         = ${System.getProperty("os.arch")}")
            Log.i(TAG, "  BOARD           = ${Build.BOARD}")
            Log.i(TAG, "  HARDWARE        = ${Build.HARDWARE}")
            Log.i(TAG, "  SDK_INT         = ${Build.VERSION.SDK_INT}")
            Log.i(TAG, "  filesDir        = ${filesDir.absolutePath}")
            Log.i(TAG, "  nativeLibDir    = ${context.applicationInfo.nativeLibraryDir}")

            // ═══ STEP 2: Find binary in nativeLibraryDir ═══
            Log.i(TAG, "── STEP 2: Locate binary in nativeLibraryDir ──")
            val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
            Log.i(TAG, "  nativeLibDir exists = ${nativeLibDir.exists()}")
            nativeLibDir.listFiles()?.forEach { f ->
                Log.i(TAG, "    ${f.name} (${f.length()} bytes)")
            } ?: Log.w(TAG, "    (empty)")

            val binaryFile = File(nativeLibDir, "libaria2c.so")
            Log.i(TAG, "  binaryFile.path     = ${binaryFile.absolutePath}")
            Log.i(TAG, "  binaryFile.exists() = ${binaryFile.exists()}")

            if (!binaryFile.exists()) {
                Log.e(TAG, "ABORT: libaria2c.so not found in nativeLibraryDir!")
                Log.e(TAG, "  Check jniLibs/arm64-v8a/libaria2c.so exists in APK")
                _engineError = "aria2c binary not found. APK may be corrupt."
                return false
            }

            // ═══ STEP 3: File state ═══
            Log.i(TAG, "── STEP 3: File state ──")
            Log.i(TAG, "  exists()     = ${binaryFile.exists()}")
            Log.i(TAG, "  isFile()     = ${binaryFile.isFile()}")
            Log.i(TAG, "  length()     = ${binaryFile.length()} bytes")
            Log.i(TAG, "  canRead()    = ${binaryFile.canRead()}")
            Log.i(TAG, "  canWrite()   = ${binaryFile.canWrite()}")
            Log.i(TAG, "  canExecute() = ${binaryFile.canExecute()}")

            if (binaryFile.length() == 0L) {
                Log.e(TAG, "ABORT: length() == 0")
                return false
            }

            // ═══ STEP 4: ELF header ═══
            Log.i(TAG, "── STEP 4: ELF header ──")
            val elfInfo = inspectElfHeader(binaryFile)
            Log.i(TAG, "  magic  = ${elfInfo.magicHex}")
            Log.i(TAG, "  class  = ${elfInfo.elfClass}")
            Log.i(TAG, "  data   = ${elfInfo.dataEncoding}")
            Log.i(TAG, "  arch   = ${elfInfo.architecture}")
            Log.i(TAG, "  desc   = ${elfInfo.description}")
            Log.i(TAG, "  valid  = ${elfInfo.isValidElf}")

            if (!elfInfo.isValidElf) {
                Log.e(TAG, "ABORT: Not a valid ELF binary")
                return false
            }

            // ═══ STEP 5: SELinux diagnosis ═══
            Log.i(TAG, "── STEP 5: SELinux / permission diagnosis ──")
            runCommand("ls", "-lZ", binaryFile.absolutePath)
            runCommand("getenforce")
            runCommand("id")
            runCommand("ls", "-Z", nativeLibDir.absolutePath)

            // ═══ STEP 6: chmod 755 ═══
            Log.i(TAG, "── STEP 6: chmod 755 ──")
            try {
                val p = ProcessBuilder("chmod", "755", binaryFile.absolutePath)
                    .redirectErrorStream(true).start()
                p.waitFor(5, TimeUnit.SECONDS)
                Log.i(TAG, "  chmod exitCode = ${p.exitValue()}")
            } catch (e: Exception) {
                Log.w(TAG, "  chmod failed: ${e.message}")
                Log.i(TAG, "  Trying File.setExecutable...")
                Log.i(TAG, "  setExecutable = ${binaryFile.setExecutable(true, false)}")
            }

            Log.i(TAG, "  Post-chmod: canExecute() = ${binaryFile.canExecute()}")

            // ═══ STEP 7: Config ═══
            Log.i(TAG, "── STEP 7: Generate config ──")
            generateConfig(options)
            Log.i(TAG, "  Config path = ${configFile.absolutePath}")
            Log.i(TAG, "  Config size = ${configFile.length()} bytes")

            // ═══ STEP 8: Build command ═══
            Log.i(TAG, "── STEP 8: ProcessBuilder command ──")
            val cmd = listOf(
                binaryFile.absolutePath,
                "--conf-path=${configFile.absolutePath}",
                "--log-level=info",
                "--log=/dev/stdout"
            )
            cmd.forEachIndexed { idx, part -> Log.i(TAG, "  CMD[$idx] = $part") }

            val downloadDir = File(context.getExternalFilesDir(null), "Motrix")
            downloadDir.mkdirs()
            Log.i(TAG, "  downloadDir = ${downloadDir.absolutePath}")
            Log.i(TAG, "  HOME        = ${filesDir.absolutePath}")

            // ═══ STEP 9: Start process ═══
            Log.i(TAG, "── STEP 9: ProcessBuilder.start() ──")
            val ldBase = context.applicationInfo.nativeLibraryDir
            val existingLd = System.getenv("LD_LIBRARY_PATH") ?: ""
            val ldLibraryPath = if (existingLd.isNotEmpty()) "$ldBase:$existingLd" else ldBase
            Log.i(TAG, "  LD_LIBRARY_PATH = $ldLibraryPath")

            val pb = ProcessBuilder(cmd)
                .directory(filesDir)
                .apply {
                    environment()["HOME"] = filesDir.absolutePath
                    environment()["LD_LIBRARY_PATH"] = ldLibraryPath
                }

            process = pb.start()
            Log.i(TAG, "  process started, isAlive=${process?.isAlive}")

            // ═══ STEP 10: Capture stdout + stderr SEPARATELY ═══
            Log.i(TAG, "── STEP 10: Capture output ──")
            val stdoutLines = mutableListOf<String>()
            val stderrLines = mutableListOf<String>()

            Thread {
                try {
                    BufferedReader(InputStreamReader(process!!.inputStream)).forEachLine {
                        Log.i(TAG, "  [STDOUT] $it")
                        synchronized(stdoutLines) { stdoutLines.add(it) }
                    }
                    Log.i(TAG, "  [STDOUT] EOF")
                } catch (e: Exception) { Log.e(TAG, "  [STDOUT] error: ${e.message}") }
            }.apply { isDaemon = true; start() }

            Thread {
                try {
                    BufferedReader(InputStreamReader(process!!.errorStream)).forEachLine {
                        Log.e(TAG, "  [STDERR] $it")
                        synchronized(stderrLines) { stderrLines.add(it) }
                    }
                    Log.i(TAG, "  [STDERR] EOF")
                } catch (e: Exception) { Log.e(TAG, "  [STDERR] error: ${e.message}") }
            }.apply { isDaemon = true; start() }

            // ═══ STEP 11: Wait ═══
            Log.i(TAG, "── STEP 11: Waiting (10 x 500ms) ──")
            for (i in 1..10) {
                Thread.sleep(500)
                if (process?.isAlive != true) {
                    val exitCode = process?.exitValue() ?: -1
                    Log.e(TAG, "╔══════════════════════════════════════════╗")
                    Log.e(TAG, "║  PROCESS DIED at check $i/10              ║")
                    Log.e(TAG, "╚══════════════════════════════════════════╝")
                    Log.e(TAG, "  exitCode = $exitCode")
                    Log.e(TAG, "  ── FULL STDOUT (${stdoutLines.size} lines) ──")
                    stdoutLines.forEach { Log.e(TAG, "    $it") }
                    Log.e(TAG, "  ── FULL STDERR (${stderrLines.size} lines) ──")
                    stderrLines.forEach { Log.e(TAG, "    $it") }
                    diagnoseFailure(binaryFile, exitCode, stdoutLines, stderrLines)
                    running = false
                    return false
                }
                Log.i(TAG, "  Check $i/10: alive=true")
            }

            running = true
            Log.i(TAG, "╔══════════════════════════════════════════╗")
            Log.i(TAG, "║  Aria2Engine.start() = SUCCESS            ║")
            Log.i(TAG, "╚══════════════════════════════════════════╝")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "╔══════════════════════════════════════════╗")
            Log.e(TAG, "║  EXCEPTION in start()                     ║")
            Log.e(TAG, "╚══════════════════════════════════════════╝")
            Log.e(TAG, "  ${e.javaClass.name}: ${e.message}")
            e.stackTrace.take(10).forEach { Log.e(TAG, "    at $it") }
            running = false
            return false
        }
        }
    }

    private var _engineError: String? = null
    val engineError: String? get() = _engineError

    private fun tryConnectToPort(): Boolean {
        try {
            val socket = java.net.Socket()
            socket.connect(java.net.InetSocketAddress("127.0.0.1", Constants.ENGINE_RPC_PORT), 1000)
            socket.close()
            return true
        } catch (_: Exception) {
            return false
        }
    }

    private fun killOrphanedProcesses() {
        Log.i(TAG, "── Killing orphaned aria2c processes ──")
        try {
            val p = ProcessBuilder("sh", "-c", "pkill -f 'aria2c\\|libaria2c'")
                .redirectErrorStream(true).start()
            p.waitFor(3, TimeUnit.SECONDS)
            val exit = p.exitValue()
            Log.i(TAG, "  pkill exit=$exit (0=found+killed, 1=none found)")
        } catch (e: Exception) {
            Log.w(TAG, "  pkill failed: ${e.message}")
        }
        // Also try to kill by port
        try {
            val p = ProcessBuilder("sh", "-c", "fuser -k ${Constants.ENGINE_RPC_PORT}/tcp 2>/dev/null || true")
                .redirectErrorStream(true).start()
            p.waitFor(3, TimeUnit.SECONDS)
            Log.i(TAG, "  fuser -k done")
        } catch (_: Exception) {}
        Thread.sleep(500)
        Log.i(TAG, "  Cleanup done")
    }

    fun stop() {
        Log.i(TAG, "stop()")
        if (!running) return
        try {
            process?.destroy()
            process?.waitFor(5, TimeUnit.SECONDS)
            if (process?.isAlive == true) process?.destroyForcibly()
            running = false
            process = null
            Log.i(TAG, "Engine stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Stop failed: ${e.message}")
        }
    }

    fun restart(options: Map<String, String> = emptyMap()) {
        stop()
        Thread.sleep(1000)
        start(options)
    }

    fun isRunning(): Boolean = running && process?.isAlive == true

    // ═══════════════════════════════════════════
    //  SELinux + execution diagnostics
    // ═══════════════════════════════════════════

    private fun runCommand(vararg cmd: String) {
        try {
            val p = ProcessBuilder(cmd.toList())
                .redirectErrorStream(true)
                .start()
            p.waitFor(3, TimeUnit.SECONDS)
            val output = p.inputStream.bufferedReader().readText().trim()
            Log.i(TAG, "  ${cmd.joinToString(" ")}")
            output.lines().forEach { Log.i(TAG, "    $it") }
        } catch (e: Exception) {
            Log.w(TAG, "  ${cmd.joinToString(" ")}: ${e.message}")
        }
    }

    private fun diagnoseFailure(binary: File, exitCode: Int, stdout: List<String>, stderr: List<String>) {
        val allOutput = (stdout + stderr).joinToString("\n").lowercase()

        when {
            allOutput.contains("exec format error") -> {
                Log.e(TAG, "  ╔═══ EXEC FORMAT ERROR ═══╗")
                Log.e(TAG, "  ║ Binary arch mismatch!     ║")
                Log.e(TAG, "  ╚══════════════════════════╝")
                Log.e(TAG, "  Device ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
                Log.e(TAG, "  Binary arch: ${inspectElfHeader(binary).architecture}")
            }
            allOutput.contains("permission denied") -> {
                Log.e(TAG, "  ╔═══ PERMISSION DENIED ═══╗")
                Log.e(TAG, "  ║ SELinux or file perm?     ║")
                Log.e(TAG, "  ╚══════════════════════════╝")
                Log.e(TAG, "  canExecute = ${binary.canExecute()}")
                runCommand("ls", "-lZ", binary.absolutePath)
                runCommand("getenforce")
            }
            allOutput.contains("no such file or directory") -> {
                Log.e(TAG, "  ╔═══ NO SUCH FILE ═══╗")
                Log.e(TAG, "  ║ Missing dependency     ║")
                Log.e(TAG, "  ╚══════════════════════╝")
            }
            exitCode == 126 -> {
                Log.e(TAG, "  ╔═══ EXIT 126: Cannot execute ═══╗")
                Log.e(TAG, "  ╚════════════════════════════════╝")
                runCommand("ls", "-lZ", binary.absolutePath)
                runCommand("getenforce")
            }
            exitCode == 127 -> {
                Log.e(TAG, "  ╔═══ EXIT 127: Command not found ═══╗")
                Log.e(TAG, "  ╚════════════════════════════════════╝")
            }
            else -> {
                Log.e(TAG, "  ╔═══ UNKNOWN FAILURE (exitCode=$exitCode) ═══╗")
                Log.e(TAG, "  ╚════════════════════════════════════════════╝")
            }
        }

        runCommand("file", binary.absolutePath)
        try {
            val re = ProcessBuilder("readelf", "-h", binary.absolutePath)
                .redirectErrorStream(true).start()
            re.waitFor(3, TimeUnit.SECONDS)
            val out = re.inputStream.bufferedReader().readText().trim()
            if (out.isNotEmpty() && !out.contains("not found")) {
                Log.e(TAG, "  readelf -h:")
                out.lines().forEach { Log.e(TAG, "    $it") }
            }
        } catch (_: Exception) {}
    }

    // ═══════════════════════════════════════════
    //  ELF header inspection
    // ═══════════════════════════════════════════

    data class ElfInfo(
        val magicHex: String, val elfClass: String, val dataEncoding: String,
        val architecture: String, val description: String, val isValidElf: Boolean
    )

    private fun inspectElfHeader(file: File): ElfInfo {
        try {
            val bytes = ByteArray(20)
            FileInputStream(file).use { fis ->
                val read = fis.read(bytes)
                if (read < 16) return ElfInfo("too few bytes ($read)", "N/A", "N/A", "N/A", "File too small", false)
            }
            val magicHex = bytes.take(4).joinToString(" ") { String.format("%02X", it) }
            val isElf = bytes[0] == 0x7F.toByte() && bytes[1] == 'E'.code.toByte() &&
                    bytes[2] == 'L'.code.toByte() && bytes[3] == 'F'.code.toByte()
            if (!isElf) {
                val ascii = bytes.take(20).map { if (it in 32..126) it.toInt().toChar() else '.' }.joinToString("")
                return ElfInfo("$magicHex ($ascii)", "N/A", "N/A", "NOT ELF", "Not an ELF: $ascii", false)
            }
            val elfClass = when (bytes[4].toInt()) { 1 -> "ELF32"; 2 -> "ELF64"; else -> "unknown(${bytes[4]})" }
            val dataEnc = when (bytes[5].toInt()) { 1 -> "LittleEndian"; 2 -> "BigEndian"; else -> "unknown(${bytes[5]})" }
            val machine = (bytes[18].toInt() and 0xFF) or ((bytes[19].toInt() and 0xFF) shl 8)
            val arch = when (machine) { 0x03 -> "x86"; 0x28 -> "ARM"; 0x3E -> "x86_64"; 0xB7 -> "AArch64"; else -> "0x${String.format("%04X", machine)}" }
            return ElfInfo(magicHex, elfClass, dataEnc, arch, "$elfClass $dataEnc $arch", true)
        } catch (e: Exception) {
            return ElfInfo("error: ${e.message}", "N/A", "N/A", "N/A", "Read failed: ${e.message}", false)
        }
    }

    // ═══════════════════════════════════════════
    //  Config generation
    // ═══════════════════════════════════════════

    private fun generateConfig(overrides: Map<String, String>) {
        val downloadDir = File(context.getExternalFilesDir(null), "Motrix")
        downloadDir.mkdirs()

        // Create session file if it doesn't exist
        if (!sessionFile.exists()) {
            sessionFile.createNewFile()
            Log.i(TAG, "  Created empty session file: ${sessionFile.absolutePath}")
        }

        val config = buildMap {
            put("enable-rpc", "true")
            put("rpc-listen-all", "false")
            put("rpc-listen-port", Constants.ENGINE_RPC_PORT.toString())
            put("rpc-allow-origin-all", "true")
            put("continue", "true")
            put("auto-save-interval", "10")
            put("save-session-interval", "10")
            put("input-file", sessionFile.absolutePath)
            put("save-session", sessionFile.absolutePath)
            put("dir", downloadDir.absolutePath)
            put("split", "16")
            put("min-split-size", "1M")
            put("max-connection-per-server", "16")
            put("max-concurrent-downloads", "5")
            put("max-tries", "5")
            put("retry-wait", "3")
            put("connect-timeout", "30")
            put("timeout", "30")
            put("file-allocation", "none")
            put("disk-cache", "16M")
            put("check-certificate", "false")
            put("content-disposition-default-utf8", "true")
            put("remote-time", "true")
            putAll(overrides)
        }

        configFile.writeText(config.entries.joinToString("\n") { (k, v) -> "$k=$v" })
    }

    companion object {
        private const val TAG = "Aria2Engine"
    }
}
