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

    @Volatile
    private var runtimeProvider: LunarRuntimeProvider? = null

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
     * Loaded classes whose binary names start with [prefix], such as `com.moonsworth.lunar.client.`.
     *
     * Lunar's classes are obfuscated and renamed on every release, so integrations locate them by
     * shape. The list is a snapshot; it is empty when not running under Lunar.
     */
    @JvmStatic
    public fun loadedClasses(prefix: String): List<Class<*>> =
        runtimeProvider?.loadedClasses(prefix) ?: emptyList()

    /**
     * Identity of the running Lunar build (a hash of `lunarBuildData.txt` visible to [loader]),
     * or `null` when it cannot be determined.
     */
    @JvmStatic
    public fun buildId(loader: ClassLoader): String? = runtimeProvider?.buildId(loader)

    /**
     * Reads the entries a mod stored with [writeCache] for the running Lunar build.
     *
     * Returns `null` when nothing is cached, the Lunar build changed, or any class fingerprinted by
     * [writeCache] no longer matches; the stale file is deleted in those cases, so callers only need
     * to rediscover and write again.
     *
     * @param namespace lowercase identifier, usually the mod id (`[a-z0-9][a-z0-9._-]*`)
     * @param loader the class loader that loads Lunar's classes
     */
    @JvmStatic
    public fun readCache(namespace: String, loader: ClassLoader): Map<String, String>? =
        runtimeProvider?.readCache(namespace, loader)

    /**
     * Persists [entries] for the running Lunar build, replacing the entries of any older build.
     *
     * @param fingerprinted classes whose bytecode must be unchanged for [readCache] to return the entries
     * @return `true` when the cache was written
     */
    @JvmStatic
    public fun writeCache(
        namespace: String,
        loader: ClassLoader,
        entries: Map<String, String>,
        fingerprinted: Collection<Class<*>>,
    ): Boolean = runtimeProvider?.writeCache(namespace, loader, entries, fingerprinted) ?: false

    /**
     * Loader integration point. This is public only because the API and loader are
     * separate artifacts; mods should not replace the installed provider.
     */
    @JvmSynthetic
    public fun installNotificationProvider(provider: LunarNotificationProvider) {
        notificationProvider = provider
    }

    /** Loader integration point; see [installNotificationProvider]. */
    @JvmSynthetic
    public fun installRuntimeProvider(provider: LunarRuntimeProvider) {
        runtimeProvider = provider
    }
}

/** Internal service contract for class lookup and build-scoped caching under Lunar. */
public interface LunarRuntimeProvider {
    public fun loadedClasses(prefix: String): List<Class<*>>

    public fun buildId(loader: ClassLoader): String?

    public fun readCache(namespace: String, loader: ClassLoader): Map<String, String>?

    public fun writeCache(
        namespace: String,
        loader: ClassLoader,
        entries: Map<String, String>,
        fingerprinted: Collection<Class<*>>,
    ): Boolean
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
