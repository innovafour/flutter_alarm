package com.hiennv.flutter_callkit_incoming

import android.app.Activity
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.content.pm.ActivityInfo
import androidx.core.content.ContextCompat


class CallkitIncomingActivity : Activity() {

    companion object {
        private const val ACTION_ENDED_CALL_INCOMING =
            "com.hiennv.flutter_callkit_incoming.ACTION_ENDED_CALL_INCOMING"

        fun getIntent(context: Context, data: Bundle): Intent =
            Intent(CallkitConstants.ACTION_CALL_INCOMING).apply {
                action = "${context.packageName}.${CallkitConstants.ACTION_CALL_INCOMING}"
                putExtra(CallkitConstants.EXTRA_CALLKIT_INCOMING_DATA, data)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

        private const val ACTION_CLOSE_INCOMING =
            "com.hiennv.flutter_callkit_incoming.ACTION_CLOSE_INCOMING"

        /** Closes the incoming screen if it is open (sent after a custom action from the notification). */
        fun getIntentClose(context: Context): Intent =
            Intent("${context.packageName}.${ACTION_CLOSE_INCOMING}").setPackage(context.packageName)

        fun getIntentEnded(context: Context, isAccepted: Boolean): Intent {
            val intent = Intent("${context.packageName}.${ACTION_ENDED_CALL_INCOMING}")
            intent.putExtra("ACCEPTED", isAccepted)
            return intent
        }
    }

    private lateinit var tvTitle: TextView
    private lateinit var tvNumber: TextView
    private lateinit var btnDismiss: Button
    private lateinit var ivIcon: ImageView
    private var llCustomActions: LinearLayout? = null
    private var closeReceiverRegistered = false
    private var customActionSent = false

    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!isFinishing) finish()
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            setTurnScreenOn(true)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        }

        transparentStatusAndNavigation()
        setContentView(R.layout.activity_callkit_incoming)
        initView()
        incomingData(intent)
        registerCloseReceiver()
    }

    private fun registerCloseReceiver() {
        try {
            ContextCompat.registerReceiver(
                this,
                closeReceiver,
                IntentFilter("${packageName}.${ACTION_CLOSE_INCOMING}"),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            closeReceiverRegistered = true
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        if (closeReceiverRegistered) {
            try {
                unregisterReceiver(closeReceiver)
            } catch (_: Exception) {
            }
            closeReceiverRegistered = false
        }
        super.onDestroy()
    }

    private fun transparentStatusAndNavigation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            setWindowFlag(
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                        WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION, true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            window.decorView.systemUiVisibility =
                (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setWindowFlag(
                (WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                        or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION), false)
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }
    }

    private fun setWindowFlag(bits: Int, on: Boolean) {
        val win: Window = window
        val winParams = win.attributes
        winParams.flags = if (on) winParams.flags or bits else winParams.flags and bits.inv()
        win.attributes = winParams
    }

    private fun incomingData(intent: Intent) {
        val data = intent.extras?.getBundle(CallkitConstants.EXTRA_CALLKIT_INCOMING_DATA)
        if (data == null) {
            finish()
            return
        }

        tvTitle.text = data.getString(CallkitConstants.EXTRA_CALLKIT_NAME_CALLER, "Notificación")
        tvNumber.text = data.getString(CallkitConstants.EXTRA_CALLKIT_HANDLE, "Tu visita va en camino")

        showCustomActions(data)

        val duration = data.getLong(CallkitConstants.EXTRA_CALLKIT_DURATION, 5000L)
        wakeLockRequest(duration)
        finishTimeout(duration)
    }

    /** With custom actions: hide the default button and stack one full-width button per action. */
    private fun showCustomActions(data: Bundle) {
        val container = llCustomActions ?: return
        val actions = Data.customActionsFromBundle(data)
        container.removeAllViews()
        if (actions.isEmpty()) {
            container.visibility = View.GONE
            return
        }
        btnDismiss.visibility = View.GONE
        container.visibility = View.VISIBLE
        val density = resources.displayMetrics.density
        val minHeightPx = (56 * density).toInt()
        val pad = (16 * density).toInt()
        val gap = (12 * density).toInt()
        actions.forEachIndexed { index, (id, label) ->
            val button = Button(this).apply {
                text = label
                isAllCaps = false
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setBackgroundResource(R.drawable.bg_button_purple)
                setMinimumHeight(minHeightPx)
                setMinHeight(minHeightPx)
                gravity = Gravity.CENTER
                setPadding(pad, pad / 2, pad, pad / 2)
                stateListAnimator = null
                setOnClickListener { onCustomAction(data, id) }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            if (index > 0) params.topMargin = gap
            container.addView(button, params)
        }
    }

    private fun onCustomAction(data: Bundle, actionId: String) {
        if (customActionSent) return
        customActionSent = true
        stopService(Intent(this, CallkitSoundPlayerService::class.java))
        sendBroadcast(
            CallkitIncomingBroadcastReceiver.getIntentCustomAction(this, data, actionId)
        )
        finish()
    }

    private fun wakeLockRequest(duration: Long) {
        val pm = applicationContext.getSystemService(POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.FULL_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "Callkit:PowerManager"
        )
        wakeLock.acquire(duration)
    }

    private fun finishTimeout(duration: Long) {
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing) finish()
        }, duration)
    }

    private fun initView() {
        tvTitle = findViewById(R.id.tvTitle)
        tvNumber = findViewById(R.id.tvNumber)
        btnDismiss = findViewById(R.id.btnDismiss)
        ivIcon = findViewById(R.id.ivIcon)
        llCustomActions = findViewById(R.id.llCustomActions)

        val bounceAnim = AnimationUtils.loadAnimation(this, R.anim.bounce)
        ivIcon.startAnimation(bounceAnim)

        btnDismiss.setOnClickListener {
            stopService(Intent(this, CallkitSoundPlayerService::class.java))
            finish()
        }
    }

    override fun onBackPressed() {}
}
