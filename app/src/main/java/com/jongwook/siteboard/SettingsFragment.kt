package com.jongwook.siteboard

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.jongwook.siteboard.databinding.FragmentSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutTop) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top + (20 * resources.displayMetrics.density).toInt())
            insets
        }

        binding.btnWatermarkMenu.setOnClickListener {
            startActivity(Intent(requireContext(), WatermarkMenuActivity::class.java))
        }
        binding.btnExportCsvMenu.setOnClickListener { exportCsv() }
        binding.btnSettingsBackupMenu.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsBackupActivity::class.java))
        }
        binding.btnCapturePrivacyMenu.setOnClickListener {
            startActivity(Intent(requireContext(), CapturePrivacySettingsActivity::class.java))
        }
        binding.btnAppInfoMenu.setOnClickListener {
            startActivity(Intent(requireContext(), AppInfoActivity::class.java))
        }
        binding.btnNotificationMenu.setOnClickListener {
            startActivity(Intent(requireContext(), NotificationSettingsActivity::class.java))
        }
        binding.btnInspectionMenu.setOnClickListener {
            startActivity(Intent(requireContext(), InspectionScheduleActivity::class.java))
        }

        animateMenuRows()
    }

    private fun animateMenuRows() {
        val rows = listOf(
            binding.btnWatermarkMenu,
            binding.btnExportCsvMenu,
            binding.btnSettingsBackupMenu,
            binding.btnCapturePrivacyMenu,
            binding.btnAppInfoMenu,
            binding.btnNotificationMenu,
            binding.btnInspectionMenu
        )
        rows.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 24f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay((index * 36L))
                .setDuration(320L)
                .setInterpolator(OvershootInterpolator(0.8f))
                .start()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun exportCsv() {
        Toast.makeText(requireContext(), "데이터 추출 중...", Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                DataExportManager.exportPostsToCsv(requireContext().applicationContext)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "CSV(엑셀) 데이터가 저장되었습니다.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "CSV 저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
