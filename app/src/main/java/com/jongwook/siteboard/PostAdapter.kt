package com.jongwook.siteboard

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
            binding.tvTitle.text = post.title
            binding.tvDate.text = post.date

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
                    }
                    context.startActivity(intent)
                }
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