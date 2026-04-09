package com.jongwook.siteboard

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jongwook.siteboard.databinding.ItemPostBinding

class PostAdapter(
    // 💡 [추가] 선택 상태가 변할 때 HomeFragment에 개수를 알려주는 콜백
    private val onSelectionChanged: (Int) -> Unit
) : ListAdapter<PostEntity, PostAdapter.ViewHolder>(DiffCallback) {

    // 💡 [추가] 다중 선택 모드 관련 변수
    var isSelectionMode = false
    val selectedItems = mutableSetOf<PostEntity>()

    inner class ViewHolder(private val binding: ItemPostBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(post: PostEntity) {
            val meta = ProjectMetaStore.get(itemView.context, post.title)
            binding.tvTitle.text = post.title
            binding.tvDate.text = post.date
            binding.tvProjectLabel.text = if (post.memo.isNullOrBlank()) "현장" else "메모 포함"
            binding.tvFavoriteLabel.visibility = if (meta.favorite) View.VISIBLE else View.GONE
            binding.tvStatusBadge.text = meta.status
            applyStatusStyle(meta.status)
            binding.tvLocation.text = post.detailLocation?.takeIf { it.isNotBlank() }
                ?: post.location?.takeIf { it.isNotBlank() }
                ?: "위치 미입력"

            try {
                binding.ivPost.setImageURI(Uri.parse(post.imageUri))
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 💡 [추가] 1. 선택 모드에 따른 체크박스(cbSelect) 껐다 켜기
            if (isSelectionMode) {
                binding.cbSelect.visibility = View.VISIBLE
                binding.cbSelect.isChecked = selectedItems.contains(post)
            } else {
                binding.cbSelect.visibility = View.GONE
                binding.cbSelect.isChecked = false
            }
            binding.viewSelectedOverlay.visibility = if (selectedItems.contains(post)) View.VISIBLE else View.GONE

            // 💡 [수정] 2. 짧게 눌렀을 때의 동작 분기
            itemView.setOnClickListener {
                if (isSelectionMode) {
                    // 선택 모드일 때는 상세화면 이동 대신 체크박스 끄고 켜기
                    toggleSelection(post)
                } else {
                    // 일반 모드일 때는 기존처럼 상세 화면(DetailActivity)으로 이동
                    val context = itemView.context
                    val intent = Intent(context, DetailActivity::class.java).apply {
                        putExtra("id", post.id)
                        putExtra("title", post.title)
                        putExtra("desc", post.description)
                        putExtra("loc", post.location)
                        putExtra("imageUri", post.imageUri)
                        putExtra("date", post.date)
                        putExtra("detailLoc", post.detailLocation)
                        putExtra("memo", post.memo)
                        putExtra("originalFileName", post.originalFileName)
                        putExtra("originalUri", post.originalUri ?: "")
                        putExtra("extraFields", post.extraFields ?: "")
                    }
                    context.startActivity(intent)
                }
            }

            binding.tvStatusBadge.setOnClickListener {
                if (isSelectionMode) return@setOnClickListener
                cycleStatus(post.title)
            }

            // 💡 [추가] 3. 꾹~ 길게 눌렀을 때 (다중 선택 모드 진입)
            itemView.setOnLongClickListener {
                if (!isSelectionMode) {
                    isSelectionMode = true
                    toggleSelection(post)
                }
                true
            }
        }

        private fun applyStatusStyle(status: String) {
            val context = itemView.context
            val (bg, text) = when (status) {
                ProjectMeta.STATUS_REPAIR -> R.color.danger_accent to R.color.bg_dark
                ProjectMeta.STATUS_CHECK -> R.color.orange_primary to R.color.bg_dark
                ProjectMeta.STATUS_DONE -> R.color.mint_accent to R.color.bg_dark
                else -> R.color.teal_accent to R.color.bg_dark
            }
            binding.tvStatusBadge.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, bg))
            binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, text))
            if (binding.tvFavoriteLabel.visibility == View.VISIBLE) {
                binding.tvFavoriteLabel.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, bg))
                binding.tvFavoriteLabel.setTextColor(ContextCompat.getColor(context, text))
            } else {
                binding.tvFavoriteLabel.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.orange_primary))
                binding.tvFavoriteLabel.setTextColor(ContextCompat.getColor(context, R.color.bg_dark))
            }
        }

        private fun cycleStatus(projectTitle: String) {
            val context = itemView.context
            val statuses = ProjectMeta.ALL_STATUSES
            val current = ProjectMetaStore.get(context, projectTitle).status
            val nextIndex = (statuses.indexOf(current).takeIf { it >= 0 } ?: 0) + 1
            val next = statuses[nextIndex % statuses.size]
            ProjectMetaStore.setStatus(context, projectTitle, next)
            binding.tvStatusBadge.text = next
            applyStatusStyle(next)
            CoroutineScope(Dispatchers.IO).launch {
                SiteboardWidgetManager.refreshAll(context.applicationContext)
            }
        }
    }

    // 💡 [추가] 선택 토글 로직
    private fun toggleSelection(post: PostEntity) {
        if (selectedItems.contains(post)) {
            selectedItems.remove(post)
            if (selectedItems.isEmpty()) {
                isSelectionMode = false // 다 빼면 일반 모드로 복귀
            }
        } else {
            selectedItems.add(post)
        }
        notifyDataSetChanged()
        onSelectionChanged(selectedItems.size) // 뷰(Fragment)에 알림
    }

    // 💡 [추가] 선택 모드 강제 종료
    fun exitSelectionMode() {
        isSelectionMode = false
        selectedItems.clear()
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    // 전체 선택 / 전체 해제 토글
    fun toggleSelectAll(currentList: List<PostEntity>) {
        if (selectedItems.size == currentList.size) {
            // 이미 전체 선택 → 전체 해제
            selectedItems.clear()
            isSelectionMode = false
        } else {
            // 전체 선택
            isSelectionMode = true
            selectedItems.clear()
            selectedItems.addAll(currentList)
        }
        notifyDataSetChanged()
        onSelectionChanged(selectedItems.size)
    }

    fun syncSelection(visibleItems: List<PostEntity>) {
        if (!isSelectionMode) return
        val visibleSet = visibleItems.toSet()
        if (selectedItems.retainAll(visibleSet)) {
            if (selectedItems.isEmpty()) {
                isSelectionMode = false
            }
            notifyDataSetChanged()
            onSelectionChanged(selectedItems.size)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPostBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<PostEntity>() {
        override fun areItemsTheSame(oldItem: PostEntity, newItem: PostEntity) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: PostEntity, newItem: PostEntity) = oldItem == newItem
    }
}
