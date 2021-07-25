package open.source.sharedscopecache

import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import open.source.sharedscopecache.disk.DiskLruCache
import open.source.sharedscopecache.http.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlin.properties.Delegates


class SharedScopeProvider : ContentProvider() {

    companion object {
        private const val APP_VERSION = 1
        private const val VALUE_COUNT = 1
        private const val MIME_TYPE = "application/octet-stream"
        private const val DEFAULT_MAX_SIZE: Long = 10 * 1024 * 1024
        private const val KEY_PARAMETER = "key"
        private const val MAX_SIZE_KEY = "max_size"
        private val GENERATE_KEY_LOCK = Any()
        private val SHA_256_CHARS by lazy { CharArray(64) }
        private val HEX_CHAR_ARRAY by lazy { "0123456789abcdef".toCharArray() }
        private val MESSAGE_DIGEST by lazy { MessageDigest.getInstance("SHA-256") }
        private val ASYNC_THREAD by lazy { HandlerThread(SharedScopeCache.MAGIC_NAME).apply { start() } }

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

    }

    private class HandlerRunner : Handler(ASYNC_THREAD.looper), NanoHTTPD.AsyncRunner {
        override fun closeAll() {
            removeCallbacksAndMessages(null)
        }

        override fun closed(clientHandler: NanoHTTPD.ClientHandler) {
            removeCallbacks(clientHandler)
        }

        override fun exec(code: NanoHTTPD.ClientHandler) {
            post(code)
        }
    }

    private val diskLruCache by lazy {
        val maxSize = context?.run {
            packageManager.getProviderInfo(
                ComponentName(
                    this,
                    SharedScopeProvider::class.java
                ), PackageManager.GET_META_DATA
            )
        }?.metaData?.getLong(MAX_SIZE_KEY, DEFAULT_MAX_SIZE) ?: DEFAULT_MAX_SIZE
        val dir = context?.cacheDir ?: File(
            System.getProperty(
                "java.io.tmpdir",
                "."
            ) ?: "."
        )
        DiskLruCache.open(
            dir,
            APP_VERSION,
            VALUE_COUNT,
            maxSize
        )
    }
    private val serverLock = Any()
    private var server: NanoHTTPD? = null

    private fun tryGetServer(): NanoHTTPD? {
        synchronized(serverLock) {
            var newInstance = server
            if (newInstance == null) {
                try {
                    newInstance = object : NanoHTTPD(0) {
                        override fun serve(session: IHTTPSession?): Response {
                            if (session != null && session.method == Method.GET) {
                                if (session.uri.endsWith(SharedScopeCache.MAGIC_NAME)) {
                                    val key =
                                        session.parameters[KEY_PARAMETER]?.firstOrNull()
                                    if (!key.isNullOrEmpty()) {
                                        val file = diskLruCache.get(key)
                                            .getFile(0)
                                        Log.d(SharedScopeCache.TAG, "response:" + file.absoluteFile)
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
                    }.apply {
                        setAsyncRunner(HandlerRunner())
                        start()
                    }
                } catch (e: Throwable) {
                    Log.d(SharedScopeCache.TAG, e.toString())
                }
            }
            server = newInstance
            return newInstance
        }
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        return null
    }

    override fun getType(uri: Uri): String? {
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val data = values?.getAsByteArray(SharedScopeCache.DATA_PARAMETER) ?: return null
        val server = tryGetServer() ?: return null
        val key = generateKey(data)
        diskLruCache.edit(key).apply {
            getFile(0).apply {
                writeBytes(data)
            }
            commit()
        }
        Log.d(SharedScopeCache.TAG, "append:" + diskLruCache.get(key).getFile(0).absoluteFile)
        return Uri.parse("http://localhost:${server.listeningPort}/${SharedScopeCache.MAGIC_NAME}")
            .buildUpon()
            .appendQueryParameter(KEY_PARAMETER, key)
            .build()
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        return 0
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        return 0
    }
}