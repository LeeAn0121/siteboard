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

                // 코틀린의 마법: 제목(title)을 기준으로 리스트를 그룹화합니다.
                // 결과 예시: "지하 2층 누수" -> [사진1, 사진2, 사진3]
                val groupedPosts = postList.groupBy { it.title }

                // 화면에 뿌려줄 문자열 리스트 생성
                val displayList = mutableListOf<String>()

                for ((title, postsInGroup) in groupedPosts) {
                    displayList.add("📁 $title \n   └ 총 ${postsInGroup.size}장의 현장 기록")
                }

                // 기본 어댑터를 사용해 ListView에 데이터 뿌리기 (간편한 UI 구성)
                val adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_list_item_1,
                    displayList
                )
                binding.lvProjects.adapter = adapter
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}