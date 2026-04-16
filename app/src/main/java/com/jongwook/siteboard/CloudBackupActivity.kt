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

class CloudBackupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCloudBackupBinding
    private lateinit var googleSignInClient: GoogleSignInClient

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        GoogleSignIn.getSignedInAccountFromIntent(result.data)
            .addOnSuccessListener { account -> updateUI(account) }
            .addOnFailureListener { e ->
                Toast.makeText(this, "로그인 실패: ${e.message}", Toast.LENGTH_SHORT).show()
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
        binding.switchAutoBackup.setOnCheckedChangeListener { _, checked ->
            CloudBackupManager.setAutoBackupEnabled(this, checked)
        }

        updateUI(GoogleSignIn.getLastSignedInAccount(this))
    }

    private fun updateUI(account: GoogleSignInAccount?) {
        if (account != null) {
            binding.layoutSignedOut.visibility = View.GONE
            binding.layoutSignedIn.visibility = View.VISIBLE
            binding.layoutUpload.visibility = View.VISIBLE
            binding.layoutAutoBackup.visibility = View.VISIBLE
            binding.layoutBackupList.visibility = View.VISIBLE
            binding.tvDisplayName.text = account.displayName ?: account.email ?: ""
            binding.tvEmail.text = account.email ?: ""
            binding.switchAutoBackup.isChecked = CloudBackupManager.isAutoBackupEnabled(this)
            loadBackupList(account)
        } else {
            binding.layoutSignedOut.visibility = View.VISIBLE
            binding.layoutSignedIn.visibility = View.GONE
            binding.layoutUpload.visibility = View.GONE
            binding.layoutAutoBackup.visibility = View.GONE
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
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                CloudBackupManager.upload(token, "siteboard_sites_$ts.json",
                    CloudBackupManager.buildSiteListJson(applicationContext))
                CloudBackupManager.upload(token, "siteboard_settings_$ts.json",
                    SettingsBackupManager.buildSettingsBackupJson(applicationContext))

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
                    Toast.makeText(this@CloudBackupActivity,
                        "업로드 실패: ${e.message}", Toast.LENGTH_LONG).show()
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
                val files = CloudBackupManager.listSiteBackups(token)

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

        // Parse ISO 8601 date to readable format
        val dateStr = try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            val parsed = sdf.parse(file.createdTime)
            SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(parsed ?: Date())
        } catch (_: Exception) { file.createdTime }

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

        row.addView(infoLayout)
        row.addView(restoreBtn)

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
                val json = CloudBackupManager.downloadFile(token, file.id)
                val count = CloudBackupManager.restoreFromJson(applicationContext, json)
                withContext(Dispatchers.Main) {
                    binding.layoutProgress.visibility = View.GONE
                    binding.btnUpload.isEnabled = true
                    Toast.makeText(this@CloudBackupActivity,
                        "복원 완료: $count 건의 기록이 복원되었습니다.", Toast.LENGTH_LONG).show()
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
}
