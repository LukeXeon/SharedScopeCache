package open.source.sharedscopecache

import android.content.ContentValues
import android.content.Context
import android.net.Uri

object SharedScopeCache {

    internal const val MAGIC_NAME = "shared-scope-cache"
    internal const val DATA_PARAMETER = "data"
    internal const val TAG = "SharedScopeCache"

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