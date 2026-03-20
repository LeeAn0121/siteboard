package com.jongwook.siteboard

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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

        // 1. ArchiveFragment에서 넘겨준 '프로젝트명(제목)' 받기
        val projectTitle = intent.getStringExtra("PROJECT_TITLE") ?: "알 수 없는 프로젝트"
        binding.tvProjectTitle.text = projectTitle

        // 2. 뒤로가기 버튼 동작
        binding.btnBack.setOnClickListener { finish() }

        // 3. 종욱님의 PostAdapter 재활용! (격자 세팅)
        postAdapter = PostAdapter()
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