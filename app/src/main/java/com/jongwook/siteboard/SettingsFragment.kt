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

                // 다운로드 폴더 안의 SITEBOARD 폴더에 파일 저장
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "Siteboard_Data_$timeStamp.csv"

                // 💡 [수정됨] 다운로드/SITEBOARD 경로 설정 및 생성 로직
                val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val siteboardDir = File(baseDir, "SITEBOARD")

                if (!siteboardDir.exists()) {
                    siteboardDir.mkdirs()
                }

                val file = File(siteboardDir, fileName)

                FileWriter(file).use { it.write(csvData.toString()) }

                withContext(Dispatchers.Main) {
                    val snackbar = com.google.android.material.snackbar.Snackbar.make(
                        requireView(),
                        "CSV(엑셀) 데이터가 저장되었습니다!",
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                    )

                    // 💡 [핵심] 삼성 갤럭시 '내 파일' 전용 다이렉트 이동 + 실패 시 일반 다운로드 폴더 이동
                    snackbar.setAction("폴더 열기") {
                        val baseDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                        val siteboardDir = java.io.File(baseDir, "SITEBOARD")
                        var isOpened = false

                        if (siteboardDir.exists()) {
                            // 1차 시도: 삼성 갤럭시 기본 앱 '내 파일(My Files)' 전용 인텐트 사용
                            try {
                                val intent = android.content.Intent("samsung.myfiles.intent.action.LAUNCH_MY_FILES")
                                intent.putExtra("samsung.myfiles.intent.extra.START_PATH", siteboardDir.absolutePath)
                                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK

                                // 이 기기에 삼성 '내 파일' 앱이 설치되어 있는지 확인
                                if (intent.resolveActivity(requireContext().packageManager) != null) {
                                    startActivity(intent)
                                    isOpened = true
                                }
                            } catch (e: Exception) {
                                // 삼성폰이 아니거나 권한 문제가 있으면 조용히 무시하고 다음 단계로
                                isOpened = false
                            }
                        }

                        // 2차 시도: 갤럭시가 아니거나, 폴더가 없거나, 접근이 막힌 경우 (안전장치)
                        if (!isOpened) {
                            try {
                                // 안드로이드 공식 다운로드 폴더 열기 (최상위)
                                val fallbackIntent = android.content.Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)
                                fallbackIntent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                startActivity(fallbackIntent)

                                // 최상위로 열렸을 경우를 대비해 사용자에게 안내 문구 표시
                                android.widget.Toast.makeText(requireContext(), "목록에서 [SITEBOARD] 폴더를 확인하세요.", android.widget.Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(requireContext(), "파일 관리자 앱을 찾을 수 없습니다.", android.widget.Toast.LENGTH_SHORT).show()
                            }
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