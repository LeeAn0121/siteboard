package com.jongwook.siteboard

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.jongwook.siteboard.databinding.FragmentSettingsBinding

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
        binding.btnDataManagementMenu.setOnClickListener {
            startActivity(Intent(requireContext(), DataManagementActivity::class.java))
        }
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
