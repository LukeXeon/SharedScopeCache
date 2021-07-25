package open.source.sharedscopecache.test

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import open.source.sharedscopecache.SharedScopeCache

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val view = findViewById<ImageView>(R.id.image)
        val uri = intent.data
        if (uri != null) {
            val bytes = SharedScopeCache.load(application, uri)
            if (bytes != null) {
                Glide.with(this).load(bytes)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .into(view)
            }
        }


    }

    companion object {
        private const val TAG = "MainActivity2"
    }
}