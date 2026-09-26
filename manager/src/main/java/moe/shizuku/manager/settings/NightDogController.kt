package moe.shizuku.manager.settings

import android.os.Parcel
import rikka.shizuku.Shizuku
import rikka.shizuku.server.ServerConstants

/**
 * Client side of the NightDog Lab Feature toggle.
 *
 * Talks to [rikka.shizuku.server.ShizukuService] via binder transactions
 * ([ServerConstants.BINDER_TRANSACTION_getNightDogEnabled] /
 * [ServerConstants.BINDER_TRANSACTION_setNightDogEnabled]).
 */
object NightDogController {

    private const val BINDER_DESCRIPTOR = "moe.shizuku.server.IShizukuService"

    fun isEnabled(): Boolean {
        val binder = Shizuku.getBinder() ?: throw IllegalStateException("Shizuku binder not available")
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(BINDER_DESCRIPTOR)
            binder.transact(ServerConstants.BINDER_TRANSACTION_getNightDogEnabled, data, reply, 0)
            reply.readException()
            return reply.readInt() != 0
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    fun setEnabled(enabled: Boolean) {
        val binder = Shizuku.getBinder() ?: throw IllegalStateException("Shizuku binder not available")
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(BINDER_DESCRIPTOR)
            data.writeInt(if (enabled) 1 else 0)
            binder.transact(ServerConstants.BINDER_TRANSACTION_setNightDogEnabled, data, reply, 0)
            reply.readException()
        } finally {
            reply.recycle()
            data.recycle()
        }
    }
}
