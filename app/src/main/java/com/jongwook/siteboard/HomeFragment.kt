package com.jongwook.siteboard

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.jongwook.siteboard.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // 어댑터와 DB 변수 선언
    private lateinit var postAdapter: PostAdapter
    private lateinit var db: AppDatabase

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 💡 1. 종욱님의 클래스에 맞게 DB 객체 가져오기 (getDatabase)
        db = AppDatabase.getDatabase(requireContext())

        // 💡 2. 파라미터 없는 ListAdapter 초기화 및 2열 격자 세팅
        postAdapter = PostAdapter()
        binding.rvPostList.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvPostList.adapter = postAdapter

        // 💡 3. 플로팅 버튼 클릭 시 서브 화면(글쓰기) 열기
        binding.btnOpenSub.setOnClickListener {
            val intent = Intent(requireContext(), SubActivity::class.java)
            startActivity(intent)
        }

        // 💡 4. [핵심] Room DB의 Flow 관찰하여 자동 새로고침!
        // 종욱님이 Dao에 Flow를 선언하셨기 때문에, 여기서 collect만 해두면
        // 새 사진을 찍고 돌아왔을 때 알아서 UI가 최신화됩니다.
        viewLifecycleOwner.lifecycleScope.launch {
            db.postDao().getAllPosts().collect { postList ->
                if (postList.isEmpty()) {
                    // 데이터가 없으면 '카메라 아이콘' 띄우기
                    binding.layoutEmpty.visibility = View.VISIBLE
                    binding.rvPostList.visibility = View.GONE
                } else {
                    // 데이터가 있으면 2열 리스트 띄우기
                    binding.layoutEmpty.visibility = View.GONE
                    binding.rvPostList.visibility = View.VISIBLE

                    // ListAdapter 전용 함수인 submitList()로 데이터 밀어넣기
                    postAdapter.submitList(postList)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // 메모리 누수 방지
    }
}