package com.simplecityapps.shuttle

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.simplecityapps.localmediaprovider.repository.LocalSongRepository
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.shuttle.ui.screens.home.HomeFragment
import com.simplecityapps.shuttle.ui.screens.library.songs.SongsFragment
import io.reactivex.disposables.CompositeDisposable

class MainActivity : AppCompatActivity() {

    private val compositeDisposable = CompositeDisposable()

    lateinit var songsRepository: SongRepository

    private val onNavigationItemSelectedListener = BottomNavigationView.OnNavigationItemSelectedListener { item ->
        when (item.itemId) {
            R.id.navigation_home -> {
                supportFragmentManager.beginTransaction().replace(R.id.mainContainer, HomeFragment.newInstance(), SongsFragment.TAG).commit()
                return@OnNavigationItemSelectedListener true
            }
            R.id.navigation_library -> {
                supportFragmentManager.beginTransaction().replace(R.id.mainContainer, SongsFragment.newInstance(), SongsFragment.TAG).commit()
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

        supportFragmentManager.beginTransaction()
            .add(R.id.mainContainer, HomeFragment())
            .commit()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            onHasPermission()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        compositeDisposable.clear()
    }

    private fun onHasPermission() {
        songsRepository = LocalSongRepository(applicationContext)

        compositeDisposable.add(songsRepository.init().subscribe())
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        onHasPermission()
    }

    companion object {
        const val TAG = "MainActivity"
    }
}
