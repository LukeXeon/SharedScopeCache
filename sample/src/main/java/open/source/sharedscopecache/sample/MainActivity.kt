package open.source.sharedscopecache.sample

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import open.source.sharedscopecache.SharedScopeCache
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        thread {
            val bitmap = BitmapFactory.decodeResource(resources, R.drawable.test)
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            Log.d(TAG, "size1=" + stream.size())
            val uri = SharedScopeCache.save(stream.toByteArray())
            Log.d(TAG, uri.toString())
            if (uri != null) {
                val bytes = SharedScopeCache.load(uri)
                Log.d(TAG, "size2=" + (bytes?.size ?: 0))
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}