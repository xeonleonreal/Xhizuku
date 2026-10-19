package moe.shizuku.manager.diagnostics

import android.os.Parcel
import rikka.shizuku.Shizuku
import rikka.shizuku.server.ServerConstants

object AuditApi {

    private const val DESCRIPTOR = "moe.shizuku.server.IShizukuService"

    data class AuditEntry(val uid: Int, val packageName: String, val decision: String, val time: Long)

    data class UsageEntry(val uid: Int, val count: Long, val lastTime: Long, val packageName: String = "")

    fun getAuditLog(): List<AuditEntry> {
        val binder = Shizuku.getBinder() ?: throw IllegalStateException("Shizuku binder not available")
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            binder.transact(ServerConstants.BINDER_TRANSACTION_getAuditLog, data, reply, 0)
            reply.readException()
            return reply.createStringArrayList().orEmpty().mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size != 4) return@mapNotNull null
                AuditEntry(
                    uid = parts[0].toIntOrNull() ?: return@mapNotNull null,
                    packageName = parts[1],
                    decision = parts[2],
                    time = parts[3].toLongOrNull() ?: 0L
                )
            }.reversed()
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    fun getUsageStats(): List<UsageEntry> {
        val binder = Shizuku.getBinder() ?: throw IllegalStateException("Shizuku binder not available")
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            binder.transact(ServerConstants.BINDER_TRANSACTION_getUsageStats, data, reply, 0)
            reply.readException()
            val size = reply.readInt()
            val out = ArrayList<UsageEntry>(size)
            repeat(size) {
                out.add(
                    UsageEntry(
                        uid = reply.readInt(),
                        count = reply.readLong(),
                        lastTime = reply.readLong(),
                        packageName = reply.readString() ?: ""
                    )
                )
            }
            return out.sortedByDescending { it.count }
        } finally {
            reply.recycle()
            data.recycle()
        }
    }
}
