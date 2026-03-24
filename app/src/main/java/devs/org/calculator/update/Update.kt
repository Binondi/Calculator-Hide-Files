package devs.org.calculator.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.text.method.LinkMovementMethod
import android.util.Log
import android.widget.TextView
import androidx.core.net.toUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import devs.org.calculator.R
import devs.org.calculator.utils.DialogUtil
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.CorePlugin
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

object Update {
    fun fetchLatestRelease(
        context: Context,
        onResult: (GitHubRelease?) -> Unit
    ) {
        val client = OkHttpClient()
        Log.d("Update","Fetching github api")

        val request = Request.Builder()
            .url("https://api.github.com/repos/Binondi/Calculator-Hide-Files/releases/latest")
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                (context as Activity).runOnUiThread {
                    onResult(null)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body.string()

                try {
                    val json = JSONObject(body)

                    val version = json.getString("tag_name")
                    val changelog = json.getString("body")
                    val url = json.getString("html_url")

                    Log.d("Update","json parsed")

                    (context as Activity).runOnUiThread {
                        onResult(GitHubRelease(version, changelog, url))
                    }

                } catch (e: Exception) {
                    Log.d("Update","Error $e")

                    (context as Activity).runOnUiThread {
                        onResult(null)
                    }
                }
            }
        })
    }

    fun isUpdateAvailable(current: String, latest: String): Boolean {
        val currentParts = current.replace("v", "").split(".")
        val latestParts = latest.replace("v", "").split(".")

        val maxLength = maxOf(currentParts.size, latestParts.size)

        for (i in 0 until maxLength) {
            val c = currentParts.getOrNull(i)?.toIntOrNull() ?: 0
            val l = latestParts.getOrNull(i)?.toIntOrNull() ?: 0

            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    fun showUpdateDialog(
        context: Context,
        release: GitHubRelease
    ) {
        val textView = TextView(context).apply {
            setPadding(60, 24, 60, 0)
            textSize = 14f
            movementMethod = LinkMovementMethod.getInstance()
            isClickable = true
            isFocusable = true
            setLineSpacing(1.2f, 1.2f)
        }

        val markwon = Markwon.builder(context)
            .usePlugin(CorePlugin.create())
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    builder.linkResolver { view, link ->
                        val intent = Intent(Intent.ACTION_VIEW, link.toUri())
                        view.context.startActivity(intent)
                    }
                }
            })
            .build()

        val cleanMarkdown = release.changelog
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .lines()
            .joinToString("\n") { it.trimStart() }
            .replace(
                Regex("\\*\\*Full Changelog\\*\\*: (https?://\\S+)"),
                "[Full Changelog]($1)"
            )

        markwon.setMarkdown(textView, cleanMarkdown)

        DialogUtil(context).showMaterialDialog(
            title = context.getString(R.string.update_available),
            view = textView,
            positiveButtonText = context.getString(R.string.download),
            neutralButtonText = context.getString(R.string.later),
            callback = object : DialogUtil.DialogCallback {
                override fun onPositiveButtonClicked() {
                    openGitHubRelease(context, release.url)
                }

                override fun onNegativeButtonClicked() {}

                override fun onNaturalButtonClicked() {}

            }
        )

    }

    fun openGitHubRelease(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        context.startActivity(intent)
    }

    fun checkForAppUpdate(context: Context, onResult: (Boolean) -> Unit) {
        fetchLatestRelease(context) { release ->
            Log.d("Update","Release $release")

            if (release == null) {
                onResult(false)
                return@fetchLatestRelease
            }

            val currentVersion = "1.4.3"

            if (isUpdateAvailable(currentVersion, release.version)) {
                onResult(true)
                showUpdateDialog(context, release)
            } else {
                onResult(false)
            }
        }
    }

    fun getVersionName(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "0.0"
        } catch (e: Exception) {
            e.printStackTrace()
            "0.0"
        }
    }
}