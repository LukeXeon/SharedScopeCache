package open.source.sharedscopecache

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Process
import android.util.Log
import open.source.sharedscopecache.disk.DiskLruCache
import open.source.sharedscopecache.http.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class SharedScopeCache(context: Context) {

    companion object {
        private const val MAGIC_NAME = "shared_scope_cache"
        private const val KEY_PARAMETER = "key"
        private const val MIME_TYPE = ""
        private const val APP_VERSION = 1
        private const val VALUE_COUNT = 1
        private const val DEFAULT_MAX_SIZE: Long = 10 * 1024 * 1024
        private val GENERATE_KEY_LOCK = Any()
        private val INSTANCE_LOCK = Any()
        private val SHA_256_CHARS = CharArray(64)
        private val MESSAGE_DIGEST = MessageDigest.getInstance("SHA-256")
        private val HEX_CHAR_ARRAY = "0123456789abcdef".toCharArray()
        private var instance: SharedScopeCache? = null
        private const val TAG = "SharedScopeCache"

        // Taken from:
        // http://stackoverflow.com/questions/9655181/convert-from-byte-array-to-hex-string-in-java
        // /9655275#9655275
        private fun sha256BytesToHex(bytes: ByteArray): String {
            var v: Int
            for (j in bytes.indices) {
                v = bytes[j].toInt() and 0xFF
                SHA_256_CHARS[j * 2] = HEX_CHAR_ARRAY[v ushr 4]
                SHA_256_CHARS[j * 2 + 1] = HEX_CHAR_ARRAY[v and 0x0F]
            }
            return String(SHA_256_CHARS)
        }

        private fun generateKey(bytes: ByteArray): String {
            synchronized(GENERATE_KEY_LOCK) {
                return sha256BytesToHex(MESSAGE_DIGEST.digest(bytes))
            }
        }

        @JvmStatic
        fun getInstance(context: Context): SharedScopeCache {
            synchronized(INSTANCE_LOCK) {
                var i = instance
                if (i == null) {
                    i = SharedScopeCache(context)
                    instance = i
                }
                return i
            }
        }
    }

    private val diskLruCache: DiskLruCache
    private val cacheService: NanoHTTPD

    init {
        val activityManager = context
            .getSystemService(Context.ACTIVITY_SERVICE)
                as ActivityManager
        val myPid = Process.myPid()
        val process = activityManager
            .runningAppProcesses
            .find { it.pid == myPid }!!
        diskLruCache = DiskLruCache.open(
            File(
                context.cacheDir,
                "${MAGIC_NAME}${File.separator}${process.processName}"
            ),
            APP_VERSION,
            VALUE_COUNT,
            DEFAULT_MAX_SIZE
        )
        cacheService = object : NanoHTTPD(0) {
            override fun serve(session: IHTTPSession?): Response {
                if (session != null && session.method == Method.GET) {
                    if (session.uri.endsWith(MAGIC_NAME)) {
                        val key = session.parameters[KEY_PARAMETER]?.firstOrNull()
                        if (!key.isNullOrEmpty()) {
                            val file = diskLruCache.get(key)
                                .getFile(0)
                            Log.d(TAG, "response:" + file.absoluteFile)
                            return newFixedLengthResponse(
                                Response.Status.OK,
                                MIME_TYPE,
                                FileInputStream(file),
                                file.length()
                            )
                        }
                    }
                }
                return super.serve(session)
            }
        }
        cacheService.start()
    }

    fun append(data: ByteArray): String {
        val key = generateKey(data)
        diskLruCache.edit(key).apply {
            getFile(0).apply {
                writeBytes(data)
            }
            commit()
        }
        Log.d(TAG, "append:" + diskLruCache.get(key).getFile(0).absoluteFile)
        return Uri.parse("http://localhost:${cacheService.listeningPort}/${MAGIC_NAME}")
            .buildUpon()
            .appendQueryParameter(KEY_PARAMETER, key)
            .build()
            .toString()
    }

}