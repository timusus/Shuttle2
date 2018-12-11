package com.simplecityapps.shuttle

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.bundleOf
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.simplecityapps.shuttle.ui.screens.home.HomeFragment

class MainActivity : AppCompatActivity() {

    private val onNavigationItemSelectedListener = BottomNavigationView.OnNavigationItemSelectedListener { item ->
        when (item.itemId) {
            R.id.navigation_home -> {
                return@OnNavigationItemSelectedListener true
            }
            R.id.navigation_library -> {
                return@OnNavigationItemSelectedListener true
            }
            R.id.navigation_folders -> {
                return@OnNavigationItemSelectedListener true
            }
            R.id.navigation_menu -> {
                return@OnNavigationItemSelectedListener true
            }
        }
        false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        val navView: BottomNavigationView = findViewById(R.id.nav_view)

        navView.setOnNavigationItemSelectedListener(onNavigationItemSelectedListener)

        bundleOf()

        supportFragmentManager.beginTransaction()
            .replace(R.id.mainContainer, HomeFragment())
            .commit()
    }
}
