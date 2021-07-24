package open.source.sharedscopecache

import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import open.source.sharedscopecache.disk.DiskLruCache
import open.source.sharedscopecache.http.NanoHTTPD
import java.io.FileInputStream

internal class SharedScopeServer(private val diskLruCache: DiskLruCache) : NanoHTTPD(0) {

    init {
        setAsyncRunner(HandlerRunner())
    }

    companion object {
        private const val MIME_TYPE = "application/octet-stream"
        private val ASYNC_THREAD by lazy { HandlerThread(SharedScopeCache.TAG).apply { start() } }
    }

    private class HandlerRunner : Handler(ASYNC_THREAD.looper), AsyncRunner {
        override fun closeAll() {
            removeCallbacksAndMessages(null)
        }

        override fun closed(clientHandler: ClientHandler) {
            removeCallbacks(clientHandler)
        }

        override fun exec(code: ClientHandler) {
            post(code)
        }
    }

    override fun serve(session: IHTTPSession?): Response {
        if (session != null && session.method == Method.GET) {
            if (session.uri.endsWith(SharedScopeCache.MAGIC_NAME)) {
                val key = session.parameters[SharedScopeCache.KEY_PARAMETER]?.firstOrNull()
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
}