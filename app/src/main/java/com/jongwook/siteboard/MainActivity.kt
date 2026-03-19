package com.jongwook.siteboard

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.jongwook.siteboard.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val postAdapter = PostAdapter()
    private val db by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. 리사이클러뷰 설정
        binding.rvPostList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = postAdapter
        }

        // 2. 글쓰기 버튼 클릭 시 SubActivity 실행
        binding.btnWrite.setOnClickListener {
            startActivity(Intent(this, SubActivity::class.java))
        }

        // 3. DB 데이터 관찰 (데이터 변경 시 어댑터에 전달)
        lifecycleScope.launch {
            db.postDao().getAll().collect { posts ->
                postAdapter.submitList(posts)
            }
        }
    }
}