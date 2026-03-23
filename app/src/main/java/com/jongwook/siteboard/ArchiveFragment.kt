package com.jongwook.siteboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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

        viewLifecycleOwner.lifecycleScope.launch {
            db.postDao().getAllPosts().collect { postList ->

                val groupedPosts = postList.groupBy { it.title }
                val titles = groupedPosts.keys.toList()

                if (titles.isEmpty()) {
                    binding.layoutEmpty.visibility = View.VISIBLE
                    binding.lvProjects.visibility = View.GONE
                } else {
                    binding.layoutEmpty.visibility = View.GONE
                    binding.lvProjects.visibility = View.VISIBLE
                }

                // 커스텀 어댑터
                val adapter = object : android.widget.ArrayAdapter<String>(
                    requireContext(), R.layout.item_project_list, R.id.tvProjectItem, titles
                ) {
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val v = super.getView(position, convertView, parent)
                        val count = groupedPosts[titles[position]]?.size ?: 0
                        v.findViewById<TextView>(R.id.tvProjectCount)?.text = "사진 ${count}장"
                        return v
                    }
                }

                binding.lvProjects.adapter = adapter

                binding.lvProjects.setOnItemClickListener { _, _, position, _ ->
                    val selectedTitle = titles[position]
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