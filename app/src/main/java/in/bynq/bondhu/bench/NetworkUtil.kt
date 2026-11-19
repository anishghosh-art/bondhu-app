package `in`.bynq.bondhu.bench

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Tiny helpers about the phone's internet connection. Needs ACCESS_NETWORK_STATE. */
object NetworkUtil {

    /** True if the phone has a working-looking internet connection right now. */
    fun isOnline(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * True if the current connection is "metered" (mobile data, or WiFi marked as limited).
     * We warn the user before big downloads on such a connection.
     */
    fun isMetered(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.isActiveNetworkMetered
        } catch (e: Throwable) {
            true // if unsure, be careful and warn
        }
    }
}
