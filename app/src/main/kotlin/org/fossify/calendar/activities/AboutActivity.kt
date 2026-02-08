package org.fossify.calendar.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import org.fossify.calendar.BuildConfig
import org.fossify.calendar.R
import org.fossify.calendar.databinding.ActivityAboutCustomBinding
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon

class AboutActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityAboutCustomBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupEdgeToEdge(padBottomSystem = listOf(binding.aboutNestedScrollview))
        setupMaterialScrollListener(
            scrollingView = binding.aboutNestedScrollview,
            topAppBar = binding.aboutAppbar
        )
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(topAppBar = binding.aboutAppbar, navigationIcon = NavigationIcon.Arrow)
        setupViews()
    }

    private fun setupViews() {
        binding.aboutAppName.text = getString(R.string.app_launcher_name)
        binding.aboutVersion.text = String.format(getString(R.string.version_label) + " %s", BuildConfig.VERSION_NAME)

        binding.aboutEmailHolder.setOnClickListener {
            val email = binding.aboutEmail.text.toString()
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$email")
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                toast(org.fossify.commons.R.string.no_app_found)
            }
        }

        binding.aboutWechatHolder.setOnClickListener {
            val wechat = binding.aboutWechat.text.toString()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("WeChat", wechat)
            clipboard.setPrimaryClip(clip)
            toast(R.string.copied_to_clipboard)
        }

        binding.aboutPaypalHolder.setOnClickListener {
            val link = getString(R.string.paypal_link)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
            try {
                startActivity(intent)
            } catch (e: Exception) {
                toast(org.fossify.commons.R.string.no_app_found)
            }
        }
    }
}
