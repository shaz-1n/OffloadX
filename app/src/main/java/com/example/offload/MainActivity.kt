package com.example.offload

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Find the Bottom Navigation View
        val navView: BottomNavigationView = findViewById(R.id.bottom_nav)

        // 2. Find the Navigation Host (the area where fragments swap)
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment

        // 3. Get the Controller
        val navController = navHostFragment.navController

        // 4. Link them together
        navView.setupWithNavController(navController)
    }
}