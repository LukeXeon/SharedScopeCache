package open.source.sharedscopecache.test

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val view = findViewById<ImageView>(R.id.image)
        Glide.with(this).load(intent.getStringExtra("url")).into(view)
    }

    companion object {
        private const val TAG = "MainActivity2"
    }
}