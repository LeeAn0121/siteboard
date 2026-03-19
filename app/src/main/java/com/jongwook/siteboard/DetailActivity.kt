package com.jongwook.siteboard

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jongwook.siteboard.databinding.ActivityDetailBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDetailBinding
    private val db by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val id = intent.getIntExtra("id", 0)
        val title = intent.getStringExtra("title") ?: ""
        val desc = intent.getStringExtra("desc") ?: ""
        val loc = intent.getStringExtra("loc") ?: ""
        val imageUri = intent.getStringExtra("imageUri") ?: ""
        val date = intent.getStringExtra("date") ?: ""

        // 사진 띄우기
        binding.ivDetailImage.setImageURI(Uri.parse(imageUri))

        // 1. 카톡/문자 공유 기능
        binding.btnShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, Uri.parse(imageUri))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "SITEBOARD 현장 기록 공유"))
        }

        // 2. 수정 기능 (SubActivity 재활용)
        binding.btnEdit.setOnClickListener {
            val editIntent = Intent(this, SubActivity::class.java).apply {
                putExtra("edit_id", id)
                putExtra("edit_title", title)
                putExtra("edit_desc", desc)
                putExtra("edit_loc", loc)
            }
            startActivity(editIntent)
            finish() // 상세화면 닫기
        }

        // 3. 삭제 기능
        binding.btnDelete.setOnClickListener {
            val postToDelete = PostEntity(id, title, desc, loc, imageUri, date)
            lifecycleScope.launch(Dispatchers.IO) {
                db.postDao().delete(postToDelete)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DetailActivity, "삭제되었습니다.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }
}