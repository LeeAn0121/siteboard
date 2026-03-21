package com.jongwook.siteboard

import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.jongwook.siteboard.databinding.FragmentSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = AppDatabase.getDatabase(requireContext())

        // SharedPreferences를 이용해 스위치 상태 저장/불러오기
        val sharedPref = requireActivity().getSharedPreferences("SiteboardPrefs", Context.MODE_PRIVATE)
        binding.switchPdfMode.isChecked = sharedPref.getBoolean("pdf_multi_mode", false)

        binding.switchPdfMode.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.edit().putBoolean("pdf_multi_mode", isChecked).apply()
        }

        // 엑셀(CSV) 내보내기 기능
        binding.btnExportCsv.setOnClickListener {
            exportDataToCsv()
        }

        // 워터마크 설정 화면으로 이동
        binding.btnWatermarkSettings.setOnClickListener {
            val intent = android.content.Intent(requireContext(), WatermarkSettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun exportDataToCsv() {
        Toast.makeText(requireContext(), "데이터 추출 중...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // DB에서 모든 데이터를 한 번만 읽어옴
                val postList = db.postDao().getAllPosts().first()

                // CSV 헤더 작성
                val csvData = StringBuilder()
                csvData.append("ID,프로젝트명(제목),작업내용,위치,날짜\n")

                // 데이터 조립
                for (post in postList) {
                    val title = post.title.replace(",", " ") // CSV 충돌 방지용 쉼표 제거
                    val desc = post.description.replace(",", " ").replace("\n", " ")
                    val loc = post.location?.replace(",", " ") ?: ""
                    csvData.append("${post.id},${title},${desc},${loc},${post.date}\n")
                }

                // 다운로드 폴더에 파일 저장
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "Siteboard_Data_$timeStamp.csv"
                val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(path, fileName)

                FileWriter(file).use { it.write(csvData.toString()) }

                withContext(Dispatchers.Main) {
                    val snackbar = com.google.android.material.snackbar.Snackbar.make(
                        requireView(),
                        "CSV(엑셀) 데이터가 저장되었습니다!",
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                    )

                    // 💡 [핵심] 다운로드 폴더로 바로가는 액션 버튼 추가
                    snackbar.setAction("폴더 열기") {
                        val intent = android.content.Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)
                        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        try {
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(requireContext(), "파일 관리자 앱을 실행할 수 없습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }

                    // 액션 버튼 글씨 색상 강조
                    snackbar.setActionTextColor(android.graphics.Color.parseColor("#FF6F00"))
                    snackbar.show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "오류 발생: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}