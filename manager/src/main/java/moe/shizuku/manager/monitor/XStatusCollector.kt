package moe.shizuku.manager.monitor

import android.os.ParcelFileDescriptor
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Reads device stats, preferably through the Xhizuku server (root/ADB) so it
 * keeps working where the app itself is restricted, with direct reads as fallback.
 */
object XStatusCollector {

    data class Snapshot(
        /** 0..100, or -1 if not known yet (first sample) */
        val cpuPercent: Float,
        /** 0..100, or -1 if the GPU counter is not available on this device */
        val gpuPercent: Float,
        val memUsedMb: Long,
        val memTotalMb: Long,
        val swapUsedMb: Long,
        val swapTotalMb: Long,
        val loadAvg: String,
        val cpuCores: Int
    )

    @Volatile
    private var lastCpuTotal: Long = -1L

    @Volatile
    private var lastCpuIdle: Long = -1L

    private const val SHELL_TIMEOUT_SECONDS = 5L

    data class ExecResult(
        val stdout: String,
        val stderr: String,
        val exitCode: Int,
        /** True when the command ran through the Xhizuku server. */
        val elevated: Boolean
    )

    /**
     * Runs a shell command through the Xhizuku server when available,
     * falling back to a local shell. Captures stdout, stderr and exit code.
     */
    fun exec(cmd: String): ExecResult {
        runViaShizukuFull(cmd)?.let { return it.copy(elevated = true) }
        return runLocalFull(cmd).copy(elevated = false)
    }

    private fun runViaShizukuFull(cmd: String): ExecResult? {
        return try {
            val binder = Shizuku.getBinder() ?: return null
            val service = IShizukuService.Stub.asInterface(binder)
            val remote = service.newProcess(arrayOf("sh", "-c", cmd), null, "/")
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(remote.outputStream).close()
            } catch (_: Throwable) {
            }
            var stdout = ""
            var stderr = ""
            val outReader = Thread {
                try {
                    stdout = ParcelFileDescriptor.AutoCloseInputStream(remote.inputStream)
                        .bufferedReader().readText()
                } catch (_: Throwable) {
                }
            }
            val errReader = Thread {
                try {
                    stderr = ParcelFileDescriptor.AutoCloseInputStream(remote.errorStream)
                        .bufferedReader().readText()
                } catch (_: Throwable) {
                }
            }
            outReader.start()
            errReader.start()
            val finished = try {
                remote.waitForTimeout(SHELL_TIMEOUT_SECONDS, TimeUnit.SECONDS.name)
            } catch (_: Throwable) {
                false
            }
            val code = if (finished) {
                try {
                    remote.exitValue()
                } catch (_: Throwable) {
                    -1
                }
            } else {
                try {
                    remote.destroy()
                } catch (_: Throwable) {
                }
                124
            }
            outReader.join(1000)
            errReader.join(1000)
            ExecResult(stdout, stderr, code, elevated = false)
        } catch (_: Throwable) {
            null
        }
    }

    private fun runLocalFull(cmd: String): ExecResult {
        return try {
            val process = ProcessBuilder("sh", "-c", cmd)
                .redirectErrorStream(false)
                .start()
            val finished = process.waitFor(SHELL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return ExecResult("", "timed out", 124, elevated = false)
            }
            val stdout = try {
                process.inputStream.bufferedReader().readText()
            } catch (_: Throwable) {
                ""
            }
            val stderr = try {
                process.errorStream.bufferedReader().readText()
            } catch (_: Throwable) {
                ""
            }
            ExecResult(stdout, stderr, process.exitValue(), elevated = false)
        } catch (e: Throwable) {
            ExecResult("", e.message ?: "failed", -1, elevated = false)
        }
    }
    private fun runLocal(cmd: String): String? {
        return try {
            val process = ProcessBuilder("sh", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(SHELL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return null
            }
            process.inputStream.bufferedReader().readText().ifBlank { null }
        } catch (_: Throwable) {
            null
        }
    }

    private fun runShell(cmd: String): String? {
        return runViaShizukuFull(cmd)?.stdout?.ifBlank { null } ?: runLocal(cmd)
    }

    /**
     * Runs a shell command through the Xhizuku server when available,
     * falling back to a local shell. Returns trimmed stdout or null.
     */
    fun runCommand(cmd: String): String? {
        return runShell(cmd)?.trim()?.ifBlank { null }
    }

    private fun readFirstLine(path: String): String? {
        try {
            val file = File(path)
            if (file.canRead()) {
                file.bufferedReader().use { return it.readLine()?.trim() }
            }
        } catch (_: Throwable) {
        }
        // Elevated shell can read nodes the app cannot.
        return runCommand("cat '$path'")?.lineSequence()?.firstOrNull()?.trim()
    }

    @Synchronized
    fun collect(): Snapshot {
        val statLine = runShell("cat /proc/stat")?.lineSequence()?.firstOrNull()
        val memInfo = runShell("cat /proc/meminfo")
        val loadAvg = runShell("cat /proc/loadavg")
            ?.trim()
            ?.split(Regex("\\s+"))
            ?.take(3)
            ?.joinToString(" ")
            .orEmpty()
        val mem = parseMemInfo(memInfo)
        return Snapshot(
            cpuPercent = cpuPercentFromStatLine(statLine),
            gpuPercent = readGpuPercent(),
            memUsedMb = mem[0],
            memTotalMb = mem[1],
            swapUsedMb = mem[2],
            swapTotalMb = mem[3],
            loadAvg = loadAvg,
            cpuCores = Runtime.getRuntime().availableProcessors()
        )
    }

    private fun cpuPercentFromStatLine(line: String?): Float {
        if (line == null) return -1f
        return try {
            val parts = line.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
            if (parts.size < 4) return -1f
            val idle = parts[3] + (parts.getOrNull(4) ?: 0L)
            val total = parts.sum()
            val lastTotal = lastCpuTotal
            val lastIdle = lastCpuIdle
            lastCpuTotal = total
            lastCpuIdle = idle
            if (lastTotal < 0 || total == lastTotal) return -1f
            val totalDelta = (total - lastTotal).toFloat()
            val idleDelta = (idle - lastIdle).toFloat()
            ((totalDelta - idleDelta) / totalDelta * 100f).coerceIn(0f, 100f)
        } catch (_: Throwable) {
            -1f
        }
    }

    private fun gpuCandidates(): List<String> {
        val candidates = mutableListOf(
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
            "/sys/class/misc/mali0/device/utilization",
            "/sys/devices/gpu.0/load",
            "/sys/kernel/gpu/gpu_busy"
        )
        try {
            File("/sys/devices/platform").listFiles()
                ?.filter { it.isDirectory && it.name.contains("mali", ignoreCase = true) }
                ?.map { File(it, "utilization") }
                ?.filter { it.exists() }
                ?.firstOrNull()
                ?.let { candidates += it.absolutePath }
        } catch (_: Throwable) {
        }
        return candidates
    }

    private fun readGpuPercent(): Float {
        for (path in gpuCandidates()) {
            val raw = readFirstLine(path) ?: continue
            val percent = when {
                '/' in raw -> {
                    val nums = raw.split('/').mapNotNull { it.trim().toFloatOrNull() }
                    if (nums.size == 2 && nums[1] != 0f) nums[0] / nums[1] * 100f else null
                }
                else -> Regex("\\d+(\\.\\d+)?").find(raw)?.value?.toFloatOrNull()
            } ?: continue
            return percent.coerceIn(0f, 100f)
        }
        return -1f
    }

    private fun parseMemInfo(memInfo: String?): LongArray {
        var memTotalKb = 0L
        var memAvailableKb = 0L
        var swapTotalKb = 0L
        var swapFreeKb = 0L
        memInfo?.lineSequence()?.forEach { line ->
            val value = line.split(Regex("\\s+")).getOrNull(1)?.toLongOrNull() ?: return@forEach
            when {
                line.startsWith("MemTotal:") -> memTotalKb = value
                line.startsWith("MemAvailable:") -> memAvailableKb = value
                line.startsWith("SwapTotal:") -> swapTotalKb = value
                line.startsWith("SwapFree:") -> swapFreeKb = value
            }
        }
        return longArrayOf(
            (memTotalKb - memAvailableKb).coerceAtLeast(0L) / 1024L,
            memTotalKb / 1024L,
            (swapTotalKb - swapFreeKb).coerceAtLeast(0L) / 1024L,
            swapTotalKb / 1024L
        )
    }

    fun formatPercent(value: Float): String {
        return if (value < 0f) "—" else "${value.toInt()}%"
    }
}
