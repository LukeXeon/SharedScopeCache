package open.source.sharedscopecache

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.util.Log
import open.source.sharedscopecache.disk.DiskLruCache
import open.source.sharedscopecache.http.NanoHTTPD
import java.io.File
import java.security.MessageDigest


class SharedScopeProvider : ContentProvider() {

    companion object {
        private const val APP_VERSION = 1
        private const val VALUE_COUNT = 1
        private const val DEFAULT_MAX_SIZE: Long = 10 * 1024 * 1024
        private val GENERATE_KEY_LOCK = Any()
        private val SHA_256_CHARS by lazy { CharArray(64) }
        private val HEX_CHAR_ARRAY by lazy { "0123456789abcdef".toCharArray() }
        private val MESSAGE_DIGEST by lazy { MessageDigest.getInstance("SHA-256") }

        // Taken from:
        // http://stackoverflow.com/questions/9655181/convert-from-byte-array-to-hex-string-in-java
        // /9655275#9655275
        private fun sha256BytesToHex(bytes: ByteArray): String {
            var v: Int
            for (j in bytes.indices) {
                v = bytes[j].toInt() and 0xFF
                SHA_256_CHARS[j * 2] = HEX_CHAR_ARRAY[v ushr 4]
                SHA_256_CHARS[j * 2 + 1] =
                    HEX_CHAR_ARRAY[v and 0x0F]
            }
            return String(SHA_256_CHARS)
        }

        private fun generateKey(bytes: ByteArray): String {
            synchronized(GENERATE_KEY_LOCK) {
                return sha256BytesToHex(MESSAGE_DIGEST.digest(bytes))
            }
        }

    }

    private val diskLruCache by lazy {
        DiskLruCache.open(
            File(
                System.getProperty(
                    "java.io.tmpdir",
                    "."
                ) ?: "."
            ),
            APP_VERSION,
            VALUE_COUNT,
            DEFAULT_MAX_SIZE
        )
    }
    private val serverLock = Any()
    private var server: NanoHTTPD? = null

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
        val server = synchronized(serverLock) {
            var s = server
            if (s == null) {
                try {
                    s = SharedScopeServer(diskLruCache).apply { start() }
                } catch (e: Throwable) {
                    Log.d(SharedScopeCache.TAG, e.toString())
                }
            }
            server = s
            return@synchronized s
        } ?: return null
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
            .appendQueryParameter(SharedScopeCache.KEY_PARAMETER, key)
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