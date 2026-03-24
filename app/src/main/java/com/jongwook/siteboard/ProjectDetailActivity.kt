package com.jongwook.siteboard

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.jongwook.siteboard.databinding.ActivityProjectDetailBinding
import kotlinx.coroutines.launch

class ProjectDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProjectDetailBinding
    private lateinit var postAdapter: PostAdapter
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProjectDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 시스템 상단바 간격 처리
        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutTop) { v, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = sb.top + (8 * resources.displayMetrics.density).toInt())
            insets
        }

        val projectTitle = intent.getStringExtra("PROJECT_TITLE") ?: "알 수 없는 현장"
        binding.tvProjectTitle.text = projectTitle

        // 2. 뒤로가기 버튼 동작
        binding.btnBack.setOnClickListener { finish() }

        // 3. 종욱님의 PostAdapter 재활용! (격자 세팅)
        // 💡 어댑터가 요구하는 필수 파라미터(콜백)를 넣어줍니다.
        postAdapter = PostAdapter { selectedCount ->
            // 폴더 상세 화면에는 아직 '일괄 삭제 상단바 UI'를 만들지 않았으므로,
            // 일단 에러가 나지 않도록 빈 칸(비활성화)으로 둡니다!
            // 나중에 폴더 안에서도 일괄 삭제가 필요해지면 여기에 로직을 넣으면 됩니다.
        }
        binding.rvProjectPosts.layoutManager = GridLayoutManager(this, 2)
        binding.rvProjectPosts.adapter = postAdapter

        // 4. DB에서 해당 프로젝트명과 일치하는 데이터만 걸러서 보여주기
        db = AppDatabase.getDatabase(this)
        lifecycleScope.launch {
            db.postDao().getAllPosts().collect { allPosts ->
                // 전체 데이터 중 제목이 클릭한 프로젝트명과 똑같은 것만 필터링!
                val filteredList = allPosts.filter { it.title == projectTitle }
                postAdapter.submitList(filteredList)
            }
        }
    }
}