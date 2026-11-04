package org.prauga.calendar

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.prauga.calendar.theme.applyTheme

import org.prauga.calendar.R
import org.prauga.calendar.databinding.SimpleFrameLayoutMaterialBinding

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)

        val binding: SimpleFrameLayoutMaterialBinding = SimpleFrameLayoutMaterialBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.include.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.preferences_menu_about)

        if (savedInstanceState == null) {
            supportFragmentManager
                    .beginTransaction()
                    .add(R.id.body_frame, AboutFragment())
                    .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

}
