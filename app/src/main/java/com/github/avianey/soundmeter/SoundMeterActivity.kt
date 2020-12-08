package com.github.avianey.soundmeter

import android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.location.Location
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.github.avianey.soundmeter.SoundMeterApplication.Companion.serviceStateObservable
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer

class SoundMeterActivity: AppCompatActivity() {

    companion object {
        const val POPUP_DISPLAYED = "popupAlreadyDisplayed"
        const val TAG_PERMISSION_FRAGMENT = "permissionDialogFragment"
        const val REQUEST_CODE_PERMISSION = 1
        const val LOCATION_UPDATE_MS = 1_000L
        const val LOCATION_UPDATE_RADIUS = 10f
    }

    private var popupAlreadyDisplayed = false

    private lateinit var startStopBtn: Button
    private lateinit var coordinatesView: TextView
    private lateinit var speedView: TextView

    private val serviceStateConsumer = Consumer<LocationService.State> { syncUI() }
    private val locationConsumer = Consumer<Location> { location ->
        coordinatesView.text = getString(R.string.location_template,
            String.format("%.6f", location.latitude),
            String.format("%.6f", location.longitude))

        speedView.text = getString(R.string.speed_template,
            String.format("%.2f", location.speed ?: 0))

        /*
        PreferenceManager.getDefaultSharedPreferences(this).edit()
            .putInt(SoundMeterSettings.SETTING_SPEED, 60)
            .apply()
         */
    }

    private var locationDisposable: Disposable? = null
    private var serviceStateDisposable: Disposable? = null

    // region lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sound_meter)
        /*
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ImageView(this@SoundMeterActivity).apply {
                setImageResource(R.mipmap.ic_launcher)
            })
        })*/
        startStopBtn = findViewById(R.id.start)
        startStopBtn.setOnClickListener {
            LocationService.startOrStop(this)
            syncUI() // TODO
        }
        popupAlreadyDisplayed = savedInstanceState?.getBoolean(POPUP_DISPLAYED) ?: false
        coordinatesView = findViewById(R.id.coordinates)
        speedView = findViewById(R.id.speed)
    }

    override fun onResume() {
        super.onResume()
        if (ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) != PERMISSION_GRANTED) {
            if (supportFragmentManager.findFragmentByTag(TAG_PERMISSION_FRAGMENT) == null
                && !popupAlreadyDisplayed) {
                supportFragmentManager
                    .beginTransaction()
                    .add(PermissionPopupFragment(), TAG_PERMISSION_FRAGMENT)
                    .commit()
                popupAlreadyDisplayed = true
            }
        }
        serviceStateDisposable = serviceStateObservable.subscribe(serviceStateConsumer)
        locationDisposable = LocationService.locationObservable.subscribe(locationConsumer)
    }

    override fun onPause() {
        super.onPause()
        serviceStateDisposable?.dispose()
        locationDisposable?.dispose()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(POPUP_DISPLAYED, popupAlreadyDisplayed)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSION) {
            if (ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) == PERMISSION_GRANTED) {
                if (ActivityCompat.checkSelfPermission(this, ACCESS_BACKGROUND_LOCATION) != PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(ACCESS_BACKGROUND_LOCATION), REQUEST_CODE_PERMISSION)
                }
            }
        }
    }

    // endregion

    // region menu

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.activity_sound_meter, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.open_settings -> {
                startActivity(Intent(this, SoundMeterSettings::class.java))
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    // endregion

    private fun syncUI() {
        startStopBtn.text = when (serviceStateObservable.value!!) {
            LocationService.State.IDLE -> getString(R.string.btn_start)
            LocationService.State.RUNNING -> getString(R.string.btn_stop)
        }
        startStopBtn.isEnabled =
                ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) == PERMISSION_GRANTED
    }

}