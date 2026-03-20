package com.jongwook.siteboard

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ScaleGestureDetector
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jongwook.siteboard.databinding.ActivityDetailBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDetailBinding
    // 💡 by lazy를 활용한 깔끔한 DB 초기화 (종욱님 코드 반영)
    private val db by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val id = intent.getIntExtra("id", -1)
        val title = intent.getStringExtra("title") ?: "제목 없음"
        val desc = intent.getStringExtra("desc") ?: "내용 없음"
        val loc = intent.getStringExtra("loc") ?: ""
        val imageUri = intent.getStringExtra("imageUri") ?: ""
        val date = intent.getStringExtra("date") ?: "날짜 없음"

        // 💡 1. UI 텍스트 데이터 매핑 (새로 만든 다크 UI 레이아웃에 데이터 입히기)
        binding.tvDetailTitle.text = title
        binding.tvDetailDate.text = "📅 $date"
        binding.tvDetailLocation.text = if (loc.isBlank()) "📍 위치 미입력" else "📍 $loc"
        binding.tvDetailDesc.text = desc

        // 💡 2. 사진 띄우기
        if (imageUri.isNotEmpty()) {
            try {
                binding.ivDetailImage.setImageURI(Uri.parse(imageUri))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 💡 3. 상단 뒤로 가기 버튼 (새 UI 요소)
        binding.btnBack.setOnClickListener { finish() }

        // 💡 4. --- 핀치 줌(확대/축소) 로직 (종욱님 코드 완벽 반영) ---
        var scaleFactor = 1.0f
        val scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                scaleFactor = Math.max(1.0f, Math.min(scaleFactor, 5.0f)) // 1배 ~ 5배까지 확대 제한
                binding.ivDetailImage.scaleX = scaleFactor
                binding.ivDetailImage.scaleY = scaleFactor
                return true
            }
        })
        binding.ivDetailImage.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            true
        }

        // 💡 5. 카톡/문자 공유 기능 (이미지 파일 자체 공유)
        binding.btnShare.setOnClickListener {
            if (imageUri.isNotEmpty()) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, Uri.parse(imageUri))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // 읽기 권한 필수!
                }
                startActivity(Intent.createChooser(shareIntent, "SITEBOARD 현장 기록 공유"))
            } else {
                Toast.makeText(this, "공유할 이미지가 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        // 💡 6. 수정 기능 (SubActivity로 기존 데이터 넘기기)
        binding.btnEdit.setOnClickListener {
            val editIntent = Intent(this, SubActivity::class.java).apply {
                putExtra("edit_id", id)
                putExtra("edit_title", title)
                putExtra("edit_desc", desc)
                putExtra("edit_loc", loc)
                putExtra("edit_imageUri", imageUri) // 기존 사진 경로 보존
                putExtra("edit_date", date)         // 기존 날짜 보존
            }
            startActivity(editIntent)
            finish() // 수정 화면으로 넘어가면 현재 디테일 화면은 깔끔하게 닫기
        }

        // 💡 7. 삭제 기능 (DB + 앨범 실제 파일 동시 삭제)
        binding.btnDelete.setOnClickListener {
            if (id == -1) return@setOnClickListener

            val postToDelete = PostEntity(id, title, desc, loc, imageUri, date)

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // 스마트폰 앨범(MediaStore)에서 실제 사진 파일 완전히 날리기
                    if (imageUri.isNotEmpty()) {
                        contentResolver.delete(Uri.parse(imageUri), null, null)
                    }
                } catch (e: Exception) {
                    e.printStackTrace() // 파일이 이미 지워졌거나 권한 문제가 있어도 앱 튕김 방지
                }

                try {
                    // DB에서 데이터 삭제
                    db.postDao().delete(postToDelete)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DetailActivity, "기록과 갤러리 원본 사진이 모두 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }
}