package open.source.sharedscopecache

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import java.io.File
import java.security.MessageDigest

class SharedScopeCache : ContentProvider() {

    companion object {
        private const val DATA_PARAMETER = "DATA"
        private const val MAGIC_NAME = "shared_scope_cache"
        private const val KEY_PARAMETER = "key"
        private const val APP_VERSION = 1
        private const val VALUE_COUNT = 1
        private const val DEFAULT_MAX_SIZE: Long = 10 * 1024 * 1024
        private val GENERATE_KEY_LOCK = Any()
        private val SHA_256_CHARS = CharArray(64)
        private val MESSAGE_DIGEST = MessageDigest.getInstance("SHA-256")
        private val HEX_CHAR_ARRAY = "0123456789abcdef".toCharArray()
        private val COLUMN_NAMES = arrayOf(DATA_PARAMETER)
        private var application: Context? = null

        @JvmStatic
        fun save(bytes: ByteArray): Uri? {
            val application = application ?: return null
            val uri = Uri.parse("content://${application.packageName}.${MAGIC_NAME}")
            return application.contentResolver.insert(uri, ContentValues().apply {
                put(DATA_PARAMETER, bytes)
            })
        }

        @JvmStatic
        fun load(uri: Uri): ByteArray? {
            val application = application ?: return null
            if (uri.host?.endsWith(MAGIC_NAME) == true) {
                val cursor = application.contentResolver.query(
                    uri,
                    null,
                    null,
                    null,
                    null
                )
                if (cursor != null) {
                    cursor.moveToFirst()
                    val data = cursor.run {
                        if (isNull(0)) null else getBlob(0)
                    }
                    cursor.close()
                    return data
                }
            }
            return null
        }

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

    private var diskLruCache: DiskLruCache? = null

    override fun onCreate(): Boolean {
        val context = context?.applicationContext
        if (context != null) {
            application = context
            diskLruCache = DiskLruCache.open(
                File(context.cacheDir, MAGIC_NAME),
                APP_VERSION,
                VALUE_COUNT,
                DEFAULT_MAX_SIZE
            )
            return false
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val diskLruCache = diskLruCache ?: return null
        val key = uri.getQueryParameter(KEY_PARAMETER) ?: return null
        return try {
            val bytes = diskLruCache
                .get(key)
                .getFile(0)
                .readBytes()
            MatrixCursor(COLUMN_NAMES, 1).apply {
                addRow(arrayOf(bytes))
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    override fun getType(uri: Uri): String? {
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        values ?: return null
        val diskLruCache = diskLruCache ?: return null
        val data = values.getAsByteArray(DATA_PARAMETER) ?: return null
        return try {
            val key = generateKey(data)
            diskLruCache.edit(key).apply {
                getFile(0).writeBytes(data)
                commit()
            }
            uri.buildUpon()
                .appendQueryParameter(KEY_PARAMETER, key)
                .build()
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
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