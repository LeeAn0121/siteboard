package com.jongwook.siteboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.jongwook.siteboard.databinding.FragmentArchiveBinding
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class ArchiveFragment : Fragment() {
    private var _binding: FragmentArchiveBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentArchiveBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = AppDatabase.getDatabase(requireContext())

        // DB 변화 감지하여 자동으로 그룹 묶기
        viewLifecycleOwner.lifecycleScope.launch {
            db.postDao().getAllPosts().collect { postList ->

                // 제목(title)을 기준으로 리스트 그룹화
                val groupedPosts = postList.groupBy { it.title }

                // 💡 [핵심 추가] 제목들만 따로 뽑아서 리스트로 만들기 (클릭 시 몇 번째 제목인지 알기 위해)
                val titles = groupedPosts.keys.toList()

                // 화면에 뿌려줄 문자열 세팅
                val displayList = titles.map { title ->
                    "📁 $title \n   └ 총 ${groupedPosts[title]?.size}장의 현장 기록"
                }

                // 어댑터 연결
                val adapter = android.widget.ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_list_item_1,
                    displayList
                )
                binding.lvProjects.adapter = adapter

                // 💡 [핵심 추가] 리스트 항목을 눌렀을 때의 동작
                binding.lvProjects.setOnItemClickListener { _, _, position, _ ->
                    // 사용자가 누른 위치(position)의 진짜 제목 가져오기
                    val selectedTitle = titles[position]

                    // 해당 프로젝트의 사진들을 모아보는 새 화면(ProjectDetailActivity)으로 이동!
                    val intent = android.content.Intent(requireContext(), ProjectDetailActivity::class.java)
                    intent.putExtra("PROJECT_TITLE", selectedTitle)
                    startActivity(intent)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}