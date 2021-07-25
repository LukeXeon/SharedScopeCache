package open.source.sharedscopecache.sample

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
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
            val uri = SharedScopeCache.append(application, stream.toByteArray()) ?: return@thread
            Log.d(TAG, uri.toString())
            runOnUiThread {
                startActivityForResult(
                    Intent(
                        Intent.ACTION_VIEW
                    ).apply {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        data = uri
                        component = ComponentName(
                            "open.source.sharedscopecache.test",
                            "open.source.sharedscopecache.test.MainActivity"
                        )
                    },
                    10086
                )
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}