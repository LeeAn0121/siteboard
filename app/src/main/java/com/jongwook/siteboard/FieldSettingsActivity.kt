package com.jongwook.siteboard

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jongwook.siteboard.databinding.ActivityFieldSettingsBinding
import com.jongwook.siteboard.databinding.ItemFieldSettingsRowBinding
import java.util.Collections

class FieldSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFieldSettingsBinding
    private lateinit var adapter: FieldAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityFieldSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutFieldHeader) { v, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = sb.top + (16 * resources.displayMetrics.density).toInt())
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutAddField) { v, insets ->
            val nb = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.updatePadding(bottom = nb.bottom + (16 * resources.displayMetrics.density).toInt())
            insets
        }

        setupRecyclerView()

        binding.btnAddField.setOnClickListener { showAddFieldDialog() }
    }

    override fun onResume() {
        super.onResume()
        adapter.reload()
    }

    private fun setupRecyclerView() {
        val fields = FieldDefManager.getFields(this).toMutableList()
        adapter = FieldAdapter(fields)

        val callback = object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder) =
                makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)

            override fun isLongPressDragEnabled() = false

            override fun onMove(rv: RecyclerView, src: RecyclerView.ViewHolder, dst: RecyclerView.ViewHolder): Boolean {
                adapter.onItemMoved(src.adapterPosition, dst.adapterPosition)
                return true
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {}
        }

        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(binding.rvFields)

        binding.rvFields.layoutManager = LinearLayoutManager(this)
        binding.rvFields.adapter = adapter
    }

    private fun showAddFieldDialog() {
        val input = EditText(this).apply { hint = "새 항목 이름 입력" }
        val padding = (20 * resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(this).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle("항목 추가")
            .setView(container)
            .setPositiveButton("추가") { _, _ ->
                val label = input.text.toString().trim()
                if (label.isBlank()) return@setPositiveButton
                adapter.addField(FieldDef(id = FieldDefManager.generateCustomId(), label = label, enabled = true))
            }
            .setNegativeButton("취소", null)
            .show()
        input.requestFocus()
    }

    // ─────────────────────────────────────────────────────────
    // RecyclerView Adapter
    // ─────────────────────────────────────────────────────────
    inner class FieldAdapter(
        private val items: MutableList<FieldDef>
    ) : RecyclerView.Adapter<FieldAdapter.VH>() {

        inner class VH(val b: ItemFieldSettingsRowBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val b = ItemFieldSettingsRowBinding.inflate(layoutInflater, parent, false)
            return VH(b)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val field = items[position]
            val b = holder.b
            val isRequired = FieldDefManager.isRequired(field.id)
            val isBuiltIn  = FieldDefManager.isBuiltIn(field.id)

            b.tvFieldIndex.text = (position + 1).toString()
            b.tvFieldLabel.text = field.label

            // 상태 텍스트
            b.tvFieldStatus.text = when {
                isRequired -> "항상 표시 · 필수"
                field.enabled -> "표시 중"
                else -> "숨김"
            }

            // 삭제 버튼 – 커스텀 항목만
            b.btnDelete.visibility = if (isBuiltIn) View.GONE else View.VISIBLE
            b.btnDelete.setOnClickListener { showDeleteConfirm(holder.adapterPosition) }

            // 스위치 – 필수 항목은 숨김
            b.switchEnabled.visibility = if (isRequired) View.GONE else View.VISIBLE
            b.switchEnabled.setOnCheckedChangeListener(null)
            b.switchEnabled.isChecked = field.enabled
            b.switchEnabled.setOnCheckedChangeListener { _, checked ->
                val pos = holder.adapterPosition
                if (pos == RecyclerView.NO_ID.toInt()) return@setOnCheckedChangeListener
                items[pos] = items[pos].copy(enabled = checked)
                b.tvFieldStatus.text = if (checked) "표시 중" else "숨김"
                save()
            }

            // 이름 변경
            b.btnRename.setOnClickListener { showRenameDialog(holder.adapterPosition) }

            // 드래그 핸들
            b.ivDragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    itemTouchHelper.startDrag(holder)
                }
                false
            }
        }

        fun onItemMoved(from: Int, to: Int) {
            Collections.swap(items, from, to)
            notifyItemMoved(from, to)
            // 번호 뱃지 갱신
            notifyItemChanged(from)
            notifyItemChanged(to)
            save()
        }

        fun addField(field: FieldDef) {
            items.add(field)
            notifyItemInserted(items.lastIndex)
            save()
        }

        fun reload() {
            val fresh = FieldDefManager.getFields(this@FieldSettingsActivity).toMutableList()
            items.clear()
            items.addAll(fresh)
            notifyDataSetChanged()
        }

        private fun showRenameDialog(position: Int) {
            if (position == RecyclerView.NO_ID.toInt() || position >= items.size) return
            val current = items[position].label
            val input = EditText(this@FieldSettingsActivity).apply {
                setText(current)
                setSelection(current.length)
                hint = "항목 이름 입력"
            }
            val padding = (20 * resources.displayMetrics.density).toInt()
            val container = android.widget.FrameLayout(this@FieldSettingsActivity).apply {
                setPadding(padding, padding / 2, padding, 0)
                addView(input)
            }
            AlertDialog.Builder(this@FieldSettingsActivity)
                .setTitle("이름 변경")
                .setView(container)
                .setPositiveButton("저장") { _, _ ->
                    val newLabel = input.text.toString().trim()
                    if (newLabel.isBlank()) return@setPositiveButton
                    items[position] = items[position].copy(label = newLabel)
                    notifyItemChanged(position)
                    save()
                }
                .setNegativeButton("취소", null)
                .show()
        }

        private fun showDeleteConfirm(position: Int) {
            if (position == RecyclerView.NO_ID.toInt() || position >= items.size) return
            AlertDialog.Builder(this@FieldSettingsActivity)
                .setTitle("항목 삭제")
                .setMessage("'${items[position].label}' 항목을 삭제하시겠습니까?")
                .setPositiveButton("삭제") { _, _ ->
                    items.removeAt(position)
                    notifyItemRemoved(position)
                    notifyItemRangeChanged(position, items.size)
                    save()
                }
                .setNegativeButton("취소", null)
                .show()
        }

        private fun save() {
            FieldDefManager.saveFields(this@FieldSettingsActivity, items)
        }
    }
}
