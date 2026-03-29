package com.jongwook.siteboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jongwook.siteboard.databinding.ItemProjectSummaryBinding

data class ProjectSummary(
    val title: String,
    val count: Int,
    val recentDate: String,
    val recentLocation: String,
    val recentPostId: Int,
    val posts: List<PostEntity>,
    val favorite: Boolean = false,
    val status: String = ProjectMeta.STATUS_NORMAL
)

class ArchiveProjectAdapter(
    private val onOpenProject: (ProjectSummary) -> Unit,
    private val onExportPdf: (ProjectSummary) -> Unit
) : ListAdapter<ProjectSummary, ArchiveProjectAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(private val binding: ItemProjectSummaryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ProjectSummary) {
            binding.tvProjectItem.text = item.title
            binding.tvProjectMeta.text = "사진 ${item.count}장 · ${item.recentLocation}"
            binding.tvProjectRecent.text = "최근 기록 ${item.recentDate}"
            binding.tvProjectBadge.text = buildList {
                if (item.favorite) add("즐겨찾기")
                add(item.status)
            }.joinToString(" · ")
            binding.tvProjectBadge.visibility = android.view.View.VISIBLE

            binding.root.setOnClickListener { onOpenProject(item) }
            binding.btnExportPdf.setOnClickListener { onExportPdf(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemProjectSummaryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<ProjectSummary>() {
        override fun areItemsTheSame(oldItem: ProjectSummary, newItem: ProjectSummary): Boolean {
            return oldItem.title == newItem.title
        }

        override fun areContentsTheSame(oldItem: ProjectSummary, newItem: ProjectSummary): Boolean {
            return oldItem == newItem
        }
    }
}
