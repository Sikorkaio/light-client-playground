package io.sikorka.android.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.sikorka.android.R
import io.sikorka.android.ui.BaseActivity

class SettingsActivity : BaseActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_settings)
    setupToolbar()
    supportFragmentManager.beginTransaction()
      .replace(R.id.settings_screen__container, SikorkaPreferences())
      .commit()
  }

  companion object {
    fun start(context: Context) {
      val intent = Intent(context, SettingsActivity::class.java)
      context.startActivity(intent)
    }
  }
}