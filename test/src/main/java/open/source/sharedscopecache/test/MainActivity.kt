package open.source.sharedscopecache.test

import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import open.source.sharedscopecache.SharedScopeCache
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val view = findViewById<View>(R.id.root)
        thread {
            val bytes =
                SharedScopeCache.load(Uri.parse("content://open.source.sharedscopecache.sample.shared_scope_cache?key=0d2785b50f9ff108f9632ef37255d6f767721075d33af9aa483e9ecbdf83af7b"))
            Log.d(TAG, "load=" + (bytes?.size ?: 0))
            if (bytes != null) {
                runOnUiThread {
                    view.background =
                        BitmapDrawable(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                }
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity2"
    }
}