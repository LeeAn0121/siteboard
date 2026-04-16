package com.jongwook.siteboard

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
import com.jongwook.siteboard.databinding.ActivitySettingsBackupBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsBackupActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBackupBinding
    private lateinit var googleSignInClient: GoogleSignInClient
    private var pendingDriveAction: (() -> Unit)? = null

    private val exportSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) exportSettingsToUri(uri)
    }

    private val importSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) importSettingsFromUri(uri)
    }

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        GoogleSignIn.getSignedInAccountFromIntent(result.data)
            .addOnSuccessListener { _: GoogleSignInAccount ->
                pendingDriveAction?.invoke()
                pendingDriveAction = null
            }
            .addOnFailureListener { e ->
                pendingDriveAction = null
                Toast.makeText(this, "로그인 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBackupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutTop) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top + (8 * resources.displayMetrics.density).toInt())
            insets
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(CloudBackupManager.DRIVE_SCOPE)
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnExportSettings.setOnClickListener {
            val name = "siteboard_settings_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
            exportSettingsLauncher.launch(name)
        }
        binding.btnDriveExportSettings.setOnClickListener {
            ensureSignedInThen { exportSettingsToDrive() }
        }
        binding.btnImportSettings.setOnClickListener {
            importSettingsLauncher.launch(arrayOf("application/json", "*/*"))
        }
        binding.btnDriveImportSettings.setOnClickListener {
            ensureSignedInThen { importSettingsFromDrive() }
        }
    }

    private fun ensureSignedInThen(action: () -> Unit) {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null && GoogleSignIn.hasPermissions(account, CloudBackupManager.DRIVE_SCOPE)) {
            action()
            return
        }
        pendingDriveAction = action
        googleSignInClient.signOut().addOnCompleteListener {
            signInLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    private fun exportSettingsToUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val json = SettingsBackupManager.buildSettingsBackupJson(this@SettingsBackupActivity)
                contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                } ?: throw IllegalStateException("출력 스트림을 열 수 없습니다.")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsBackupActivity, "설정 파일을 저장했습니다.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsBackupActivity, "설정 내보내기 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun importSettingsFromUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val raw = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalStateException("파일을 읽을 수 없습니다.")
                applyImportedSettings(raw)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsBackupActivity, "설정을 불러왔습니다.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsBackupActivity, "설정 불러오기 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun exportSettingsToDrive() {
        val account = GoogleSignIn.getLastSignedInAccount(this) ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = CloudBackupManager.getToken(applicationContext, account)
                    ?: throw IllegalStateException("토큰을 가져올 수 없습니다.")
                val folders = CloudBackupManager.findOrCreateBackupFolders(applicationContext, token)
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                CloudBackupManager.upload(
                    token,
                    "siteboard_settings_$ts.json",
                    SettingsBackupManager.buildSettingsBackupJson(applicationContext),
                    folders.settingsId
                )
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsBackupActivity, "Drive에 설정을 백업했습니다.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: UserRecoverableAuthException) {
                withContext(Dispatchers.Main) {
                    pendingDriveAction = { exportSettingsToDrive() }
                    signInLauncher.launch(e.intent)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsBackupActivity, "Drive 백업 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun importSettingsFromDrive() {
        val account = GoogleSignIn.getLastSignedInAccount(this) ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = CloudBackupManager.getToken(applicationContext, account)
                    ?: throw IllegalStateException("토큰을 가져올 수 없습니다.")
                val latest = CloudBackupManager.findLatestSettingsBackup(applicationContext, token)
                    ?: throw IllegalStateException("Drive 설정 백업 파일이 없습니다.")
                val raw = CloudBackupManager.downloadFile(token, latest.id)
                applyImportedSettings(raw)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsBackupActivity, "Drive에서 최신 설정을 복원했습니다.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: UserRecoverableAuthException) {
                withContext(Dispatchers.Main) {
                    pendingDriveAction = { importSettingsFromDrive() }
                    signInLauncher.launch(e.intent)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsBackupActivity, "Drive 복원 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun applyImportedSettings(raw: String) {
        SettingsBackupManager.applySettingsBackupJson(this@SettingsBackupActivity, raw)
        ReminderScheduler.setEnabled(
            applicationContext,
            ReminderScheduler.isEnabled(applicationContext)
        )
        SiteboardWidgetManager.refreshAll(applicationContext)
        AppDatabase.backupNow(applicationContext)
    }
}
