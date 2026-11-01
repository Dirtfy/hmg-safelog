package com.hmg.safelog.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "SafeLog PoC: 서비스 시작됨" })
        startForegroundService(Intent(this, SafeLogService::class.java))
    }
}
