package net.weavemc.api.lunar

/**
 * Optional integration points exposed when Weave is running inside Lunar Client.
 *
 * The API remains safe to call on every supported client. Methods return `false`
 * when the requested Lunar feature is unavailable.
 */
public object LunarClient {
    @Volatile
    private var notificationProvider: LunarNotificationProvider? = null

    /** Whether the loader detected Lunar Client and installed its integration provider. */
    @get:JvmStatic
    public val isRunning: Boolean
        get() = notificationProvider != null

    /** Current availability of Lunar's native notification system. */
    @get:JvmStatic
    public val notificationStatus: LunarNotificationStatus
        get() = notificationProvider?.status ?: LunarNotificationStatus.NOT_LUNAR

    /**
     * Displays a native Lunar toast.
     *
     * Discovery is lazy because Lunar's notification classes are not available during
     * Weave's early bootstrap. A failed early attempt is retried later at a bounded rate.
     *
     * @return `true` when Lunar accepted the notification, otherwise `false`.
     */
    @JvmStatic
    public fun sendNotification(title: String, message: String): Boolean =
        notificationProvider?.send(title, message) ?: false

    /** Displays a native Lunar toast using `Weave` as its title. */
    @JvmStatic
    public fun sendNotification(message: String): Boolean = sendNotification("Weave", message)

    /**
     * Loader integration point. This is public only because the API and loader are
     * separate artifacts; mods should not replace the installed provider.
     */
    @JvmSynthetic
    public fun installNotificationProvider(provider: LunarNotificationProvider) {
        notificationProvider = provider
    }
}

/** Availability state for Lunar's native toast system. */
public enum class LunarNotificationStatus {
    /** The game is not running under Lunar Client. */
    NOT_LUNAR,

    /** Lunar was detected, but its notification manager has not loaded or resolved yet. */
    NOT_READY,

    /** Native Lunar notifications are ready for use. */
    READY,
}

/** Internal service contract between the common API and the Lunar loader integration. */
public interface LunarNotificationProvider {
    /** Current service availability. */
    public val status: LunarNotificationStatus

    /** Sends a notification through Lunar's native notification manager. */
    public fun send(title: String, message: String): Boolean
}
