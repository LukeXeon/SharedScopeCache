package open.source.sharedscopecache

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log
import open.source.sharedscopecache.disk.DiskLruCache
import java.io.File
import java.security.MessageDigest


class SharedScopeCache : ContentProvider() {

    companion object {
        private const val MAGIC_NAME = "shared-scope-cache"
        private const val DATA_PARAMETER = "data"
        private const val TAG = "SharedScopeCache"
        private const val APP_VERSION = 1
        private const val VALUE_COUNT = 1
        private const val DEFAULT_MAX_SIZE: Long = 10 * 1024 * 1024
        private const val KEY_PARAMETER = "key"
        private val COLUMN_NAMES = arrayOf(KEY_PARAMETER, DATA_PARAMETER)
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
        fun load(context: Context, uri: Uri): Map<String, ByteArray>? {
            if (uri.authority?.endsWith(MAGIC_NAME) == true) {
                val application = context.applicationContext
                val cursor = application.contentResolver.query(
                        uri,
                        null,
                        null,
                        null,
                        null
                )
                if (cursor != null && cursor.count > 0) {
                    val map = HashMap<String, ByteArray>(cursor.count)
                    while (cursor.moveToNext()) {
                        var bytes: ByteArray? = null
                        var key: String? = null
                        if (!cursor.isNull(0)) {
                            key = cursor.getString(0)
                        }
                        if (!cursor.isNull(1)) {
                            bytes = cursor.getBlob(1)
                        }
                        if (!key.isNullOrEmpty() && bytes != null && bytes.isNotEmpty()) {
                            map[key] = bytes
                        }
                    }
                    cursor.close()
                    return map
                }
            }
            return null
        }

        @JvmStatic
        fun append(context: Context, bytes: ByteArray): Uri? {
            val application = context.applicationContext
            return application.contentResolver
                    .insert(
                            Uri.parse("content://${application.packageName}.${MAGIC_NAME}"),
                            ContentValues().apply {
                                put(DATA_PARAMETER, bytes)
                            }
                    )
        }
    }

    private val diskLruCache by lazy {
        DiskLruCache.open(
                context?.cacheDir ?: File(
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
        val keys = uri.getQueryParameters(KEY_PARAMETER)
        if (!keys.isNullOrEmpty()) {
            val cursor = MatrixCursor(COLUMN_NAMES, keys.size)
            for (key in keys) {
                val entry = diskLruCache.get(key)
                if (entry != null) {
                    val bytes = entry.getFile(0).readBytes()
                    cursor.addRow(arrayOf<Any>(key, bytes))
                }
            }
            return cursor
        }
        return null
    }

    override fun getType(uri: Uri): String? {
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val context = context ?: return null
        val data = values?.getAsByteArray(DATA_PARAMETER) ?: return null
        val key = generateKey(data)
        diskLruCache.edit(key).apply {
            getFile(0).apply {
                writeBytes(data)
            }
            commit()
        }
        Log.d(TAG, "append:" + diskLruCache.get(key).getFile(0).absoluteFile)
        return Uri.parse("content://${context.packageName}.${MAGIC_NAME}")
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