package com.jongwook.siteboard

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jongwook.siteboard.databinding.ItemPostBinding

class PostAdapter : ListAdapter<PostEntity, PostAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(private val binding: ItemPostBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(post: PostEntity) {
            binding.tvTitle.text = post.title
            binding.tvDate.text = post.date
            // 저장된 Uri 문자열을 파싱해서 이미지 표시
            binding.ivPost.setImageURI(Uri.parse(post.imageUri))
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