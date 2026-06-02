package com.ginger.android.ui.requests

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.ginger.android.R
import com.ginger.android.databinding.ActivityImagePreviewBinding

class ImagePreviewActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_IMAGE_URI = "extra_image_uri"
    }

    private lateinit var binding: ActivityImagePreviewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImagePreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uri = intent?.getStringExtra(EXTRA_IMAGE_URI)
        if (!uri.isNullOrEmpty()) {
            binding.imagePreview.load(uri) {
                crossfade(true)
            }
        }

        binding.buttonClosePreview.setOnClickListener { finish() }
    }
}
