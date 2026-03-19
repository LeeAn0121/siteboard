package com.jongwook.siteboard

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.jongwook.siteboard.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val postAdapter = PostAdapter()
    private val db by lazy { AppDatabase.getDatabase(this) }

    // 원본 데이터를 보관할 리스트
    private var allPosts: List<PostEntity> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initRecyclerView()
        observePosts()
        setupSearch()

        binding.btnOpenSub.setOnClickListener {
            startActivity(Intent(this, SubActivity::class.java))
        }
    }

    private fun initRecyclerView() {
        binding.rvPostList.apply {
            // [UX 개선] 1열 리스트에서 2열 격자(Grid) 뷰로 변경!
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            adapter = postAdapter
            setHasFixedSize(true)
        }
    }

    private fun observePosts() {
        lifecycleScope.launch {
            db.postDao().getAllPosts().collect { postList ->
                allPosts = postList
                filterList(binding.etSearch.text.toString()) // 데이터가 갱신되면 검색어에 맞게 필터링
            }
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterList(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    // [기능 추가] 검색 필터링 및 텅 빈 화면(Empty State) 처리
    private fun filterList(query: String) {
        val filtered = if (query.isEmpty()) {
            allPosts
        } else {
            allPosts.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.location?.contains(query, ignoreCase = true) == true
            }
        }

        postAdapter.submitList(filtered)

        // 검색 결과가 없거나 게시글이 0개면 안내 문구 표시
        if (filtered.isEmpty()) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.rvPostList.visibility = View.GONE
        } else {
            binding.layoutEmpty.visibility = View.GONE
            binding.rvPostList.visibility = View.VISIBLE
        }
    }
}