package com.jongwook.siteboard

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.jongwook.siteboard.databinding.ActivityCloudBackupBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class CloudBackupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCloudBackupBinding
    private lateinit var googleSignInClient: GoogleSignInClient

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        GoogleSignIn.getSignedInAccountFromIntent(result.data)
            .addOnSuccessListener { account -> updateUI(account) }
            .addOnFailureListener { e ->
                val message = GoogleSignInErrorFormatter.format(e)
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCloudBackupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutHeader) { v, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = sb.top + (8 * resources.displayMetrics.density).toInt())
            insets
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(CloudBackupManager.DRIVE_SCOPE)
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSignIn.setOnClickListener {
            signInLauncher.launch(googleSignInClient.signInIntent)
        }
        binding.btnSignOut.setOnClickListener {
            googleSignInClient.signOut().addOnCompleteListener { updateUI(null) }
        }
        binding.btnUpload.setOnClickListener { startUpload() }
        binding.btnRefreshList.setOnClickListener {
            val account = GoogleSignIn.getLastSignedInAccount(this) ?: return@setOnClickListener
            loadBackupList(account)
        }
        updateUI(GoogleSignIn.getLastSignedInAccount(this))
    }

    private fun updateUI(account: GoogleSignInAccount?) {
        if (account != null) {
            binding.layoutSignedOut.visibility = View.GONE
            binding.layoutSignedIn.visibility = View.VISIBLE
            binding.layoutUpload.visibility = View.VISIBLE
            binding.layoutBackupList.visibility = View.VISIBLE
            binding.tvDisplayName.text = account.displayName ?: account.email ?: ""
            binding.tvEmail.text = account.email ?: ""
            loadBackupList(account)
        } else {
            binding.layoutSignedOut.visibility = View.VISIBLE
            binding.layoutSignedIn.visibility = View.GONE
            binding.layoutUpload.visibility = View.GONE
            binding.layoutBackupList.visibility = View.GONE
        }
    }

    private fun startUpload() {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account == null || !GoogleSignIn.hasPermissions(account, CloudBackupManager.DRIVE_SCOPE)) {
            googleSignInClient.signOut().addOnCompleteListener {
                signInLauncher.launch(googleSignInClient.signInIntent)
            }
            return
        }
        performUpload(account)
    }

    private fun performUpload(account: GoogleSignInAccount) {
        binding.layoutProgress.visibility = View.VISIBLE
        binding.tvProgressMessage.text = "업로드 중..."
        binding.btnUpload.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = CloudBackupManager.getToken(applicationContext, account)
                    ?: throw Exception("토큰을 가져올 수 없습니다.")
                
                // 1. 이미지 업로드 (진행률 표시 가능)
                val folders = CloudBackupManager.findOrCreateBackupFolders(applicationContext, token)
                val imageIdMap = CloudBackupManager.uploadImages(applicationContext, token, folders.imagesId) { current, total ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        binding.tvProgressMessage.text = "이미지 업로드 중... ($current/$total)"
                    }
                }

                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                CloudBackupManager.upload(token, "siteboard_sites_$ts.json",
                    CloudBackupManager.buildSiteListJson(applicationContext, imageIdMap), folders.settingsId)
                CloudBackupManager.upload(token, "siteboard_settings_$ts.json",
                    SettingsBackupManager.buildSettingsBackupJson(applicationContext), folders.settingsId)

                withContext(Dispatchers.Main) {
                    binding.layoutProgress.visibility = View.GONE
                    binding.btnUpload.isEnabled = true
                    Toast.makeText(this@CloudBackupActivity,
                        "Google Drive 업로드 완료!", Toast.LENGTH_LONG).show()
                    loadBackupList(account)
                }
            } catch (e: UserRecoverableAuthException) {
                withContext(Dispatchers.Main) {
                    binding.layoutProgress.visibility = View.GONE
                    binding.btnUpload.isEnabled = true
                    signInLauncher.launch(e.intent)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.layoutProgress.visibility = View.GONE
                    binding.btnUpload.isEnabled = true
                    val msg = e.message ?: ""
                    if (msg.contains("quota", ignoreCase = true) || msg.contains("full", ignoreCase = true)) {
                        SiteboardNotificationManager.showStorageFullNotification(this@CloudBackupActivity)
                        Toast.makeText(this@CloudBackupActivity, "드라이브 용량이 부족합니다.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@CloudBackupActivity, "업로드 실패: $msg", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun loadBackupList(account: GoogleSignInAccount) {
        binding.containerBackupItems.removeAllViews()
        binding.tvNoBackups.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = CloudBackupManager.getToken(applicationContext, account)
                    ?: return@launch
                val files = CloudBackupManager.listSiteBackups(applicationContext, token)

                withContext(Dispatchers.Main) {
                    if (files.isEmpty()) {
                        binding.tvNoBackups.visibility = View.VISIBLE
                    } else {
                        files.forEach { file -> addBackupItem(file, token) }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvNoBackups.text = "목록을 불러오지 못했습니다."
                    binding.tvNoBackups.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun addBackupItem(file: CloudBackupManager.DriveFile, token: String) {
        val dp = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = (10 * dp).toInt() }
        }

        val infoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameView = TextView(this).apply {
            text = file.name
            setTextColor(androidx.core.content.ContextCompat.getColor(this@CloudBackupActivity, R.color.text_primary))
            textSize = 13f
        }

        val dateStr = formatBackupTimestamp(file)

        val dateView = TextView(this).apply {
            text = dateStr
            setTextColor(androidx.core.content.ContextCompat.getColor(this@CloudBackupActivity, R.color.text_secondary))
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = (2 * dp).toInt() }
        }

        infoLayout.addView(nameView)
        infoLayout.addView(dateView)

        val restoreBtn = Button(this).apply {
            text = "복원"
            textSize = 12f
            setTextColor(androidx.core.content.ContextCompat.getColor(this@CloudBackupActivity, R.color.orange_primary))
            background = null
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (36 * dp).toInt()
            )
            setOnClickListener { confirmRestore(file, token) }
        }

        val deleteBtn = Button(this).apply {
            text = "삭제"
            textSize = 12f
            setTextColor(androidx.core.content.ContextCompat.getColor(this@CloudBackupActivity, android.R.color.holo_red_light))
            background = null
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (36 * dp).toInt()
            ).also { it.marginStart = (4 * dp).toInt() }
            setOnClickListener { confirmDelete(file, token) }
        }

        row.addView(infoLayout)
        row.addView(restoreBtn)
        row.addView(deleteBtn)

        // Divider above item (except first)
        if (binding.containerBackupItems.childCount > 0) {
            val divider = View(this).apply {
                setBackgroundColor(0x20808080)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
                ).also { it.topMargin = (4 * dp).toInt() }
            }
            binding.containerBackupItems.addView(divider)
        }

        binding.containerBackupItems.addView(row)
    }

    private fun formatBackupTimestamp(file: CloudBackupManager.DriveFile): String {
        val fileNameTimestamp = file.name
            .substringAfter("siteboard_sites_", "")
            .substringBefore(".json", "")

        try {
            if (fileNameTimestamp.matches(Regex("\\d{8}_\\d{6}"))) {
                val parsed = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .parse(fileNameTimestamp)
                if (parsed != null) {
                    return SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault()).format(parsed)
                }
            }
        } catch (_: Exception) {
        }

        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val parsed = sdf.parse(file.createdTime)
            SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault()).format(parsed ?: Date())
        } catch (_: Exception) {
            file.createdTime
        }
    }

    private fun confirmRestore(file: CloudBackupManager.DriveFile, token: String) {
        AlertDialog.Builder(this)
            .setTitle("백업 복원")
            .setMessage("'${file.name}'\n\n이 백업으로 복원하면 현재 데이터가 모두 덮어씌워집니다.\n계속하시겠습니까?")
            .setPositiveButton("복원") { _, _ -> performRestore(file, token) }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun performRestore(file: CloudBackupManager.DriveFile, token: String) {
        binding.layoutProgress.visibility = View.VISIBLE
        binding.tvProgressMessage.text = "복원 중..."
        binding.btnUpload.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. 데이터 복원 (Posts)
                val json = CloudBackupManager.downloadFile(token, file.id)
                val count = CloudBackupManager.restoreFromJson(applicationContext, json, token)

                // 2. 설정 복원 시도 (파일명에서 타임스탬프 추출)
                val ts = file.name.substringAfter("siteboard_sites_", "").substringBefore(".json", "")
                if (ts.isNotEmpty()) {
                    val settingsFileName = "siteboard_settings_$ts.json"
                    val settingsFolderId = CloudBackupManager.findOrCreateBackupFolders(applicationContext, token).settingsId
                    val settingsFile = CloudBackupManager.findFileByName(token, settingsFileName, settingsFolderId)
                    if (settingsFile != null) {
                        val settingsJson = CloudBackupManager.downloadFile(token, settingsFile)
                        SettingsBackupManager.applySettingsBackupJson(applicationContext, settingsJson)
                    }
                }

                withContext(Dispatchers.Main) {
                    binding.layoutProgress.visibility = View.GONE
                    binding.btnUpload.isEnabled = true
                    Toast.makeText(this@CloudBackupActivity,
                        "복원 완료: $count 건의 기록과 설정이 복원되었습니다.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.layoutProgress.visibility = View.GONE
                    binding.btnUpload.isEnabled = true
                    Toast.makeText(this@CloudBackupActivity,
                        "복원 실패: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun confirmDelete(file: CloudBackupManager.DriveFile, token: String) {
        AlertDialog.Builder(this)
            .setTitle("백업 삭제")
            .setMessage("'${file.name}'\n\n이 백업 파일을 정말 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.")
            .setPositiveButton("삭제") { _, _ -> performDelete(file, token) }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun performDelete(file: CloudBackupManager.DriveFile, token: String) {
        binding.layoutProgress.visibility = View.VISIBLE
        binding.tvProgressMessage.text = "삭제 중..."
        binding.btnUpload.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                CloudBackupManager.deleteFile(token, file.id)

                // 연결된 settings 파일도 삭제 시도
                val ts = file.name.substringAfter("siteboard_sites_", "").substringBefore(".json", "")
                if (ts.isNotEmpty()) {
                    val settingsFileName = "siteboard_settings_$ts.json"
                    val settingsFolderId = CloudBackupManager.findOrCreateBackupFolders(applicationContext, token).settingsId
                    val settingsFileId = CloudBackupManager.findFileByName(token, settingsFileName, settingsFolderId)
                    if (settingsFileId != null) {
                        CloudBackupManager.deleteFile(token, settingsFileId)
                    }
                }

                withContext(Dispatchers.Main) {
                    binding.layoutProgress.visibility = View.GONE
                    binding.btnUpload.isEnabled = true
                    Toast.makeText(this@CloudBackupActivity,
                        "백업 파일 삭제 완료: ${file.name}", Toast.LENGTH_LONG).show()
                    val account = GoogleSignIn.getLastSignedInAccount(this@CloudBackupActivity)
                    if (account != null) loadBackupList(account)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.layoutProgress.visibility = View.GONE
                    binding.btnUpload.isEnabled = true
                    Toast.makeText(this@CloudBackupActivity,
                        "백업 파일 삭제 실패: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
