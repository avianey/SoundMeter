package com.github.avianey.soundmeter

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.*
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.AsyncTask
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

class LocationService: Service() {

    enum class State {
        IDLE, RUNNING
    }

    companion object {
        private val TAG = LocationService::class.java.simpleName

        const val LOCATION_ACTION = "com.github.avianey.soundmeter.LOCATION_ACTION"
        val EMPTY_LOCATION = Location("")
        const val NOTIFICATION_TAG_SPEED_THRESHOLD = "123"
        var COUNT = 0

        fun startOrStop(context: Context) {
            Intent(context, LocationService::class.java).let { intent ->
                when (SoundMeterApplication.serviceStateObservable.value!!) {
                    State.IDLE -> {
                        Observable
                                .fromCallable {
                                    SoundMeterApplication.db.locationDao().deleteAll()
                                }
                                .subscribeOn(Schedulers.computation())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe { context.startService(intent) }
                    }
                    State.RUNNING -> context.stopService(intent)
                }
            }
        }

        var locationObservable = BehaviorSubject.createDefault(EMPTY_LOCATION)
    }

    private lateinit var locationManager: LocationManager
    private lateinit var locationReceiver: BroadcastReceiver
    private val speedThresholdListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SoundMeterSettings.SETTING_SPEED) {
            locationObservable.value?.let {  location ->
                if (location !== EMPTY_LOCATION) {
                    checkSpeed(this, location)
                }
            }
        }
    }

    // region lifecycle

    override fun onBind(intent: Intent?): IBinder? {
        throw IllegalStateException("Should not be bound")
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        locationReceiver = LocationBroadcastReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        registerReceivers()
        requestLocationUpdates()
        startForeground(42, getPersistentNotification())
        SoundMeterApplication.serviceStateObservable.onNext(State.RUNNING)
        PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(speedThresholdListener)
        Log.d(TAG, "Service started")
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Destroying service")
        super.onDestroy()
        stopLocationUpdates()
        unregisterReceivers()
        stopForeground(true)
        SoundMeterApplication.serviceStateObservable.onNext(State.IDLE)
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(speedThresholdListener)
    }

    // endregion

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates() {
        locationManager.requestLocationUpdates(
            SoundMeterActivity.LOCATION_UPDATE_MS,
            SoundMeterActivity.LOCATION_UPDATE_RADIUS,
            Criteria().apply {
                accuracy = Criteria.ACCURACY_FINE
            }, getLocationPendingIntent())
    }

    private fun stopLocationUpdates() {
        locationManager.removeUpdates(getLocationPendingIntent())
    }

    private fun registerReceivers() {
        registerReceiver(locationReceiver, IntentFilter(LOCATION_ACTION))
    }

    private fun unregisterReceivers() {
        unregisterReceiver(locationReceiver)
    }

    private fun getPersistentNotification() =
        NotificationCompat.Builder(this, SoundMeterApplication.NOTIFICATION_CHANNEL)
            .setContentTitle(getString(R.string.notification_title))
            .setContentIntent(getActivityPendingIntent())
            .setColorized(true)
            .setColor(resources.getColor(R.color.purple_500))
            .build()

    private fun getLocationPendingIntent() =
        PendingIntent.getBroadcast(this, 0,
            Intent(LOCATION_ACTION), PendingIntent.FLAG_UPDATE_CURRENT)

    private fun getActivityPendingIntent() =
        PendingIntent.getActivity(this, 0,
            Intent(this, SoundMeterActivity::class.java),
            PendingIntent.FLAG_CANCEL_CURRENT)


    private inner class LocationBroadcastReceiver: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            intent?.extras?.getParcelable<Location>(LocationManager.KEY_LOCATION_CHANGED)?.let { location ->
                Log.d(TAG, "Location received : $location")
                locationObservable.onNext(location)
                checkSpeed(context, location)

                // coroutine
                // rx
                // thread
                // asynctask
                Observable
                    .fromCallable {
                        SoundMeterApplication.db.locationDao().insert(
                            LocationEntity(
                                time = location.time,
                                lat = location.latitude,
                                lng = location.longitude,
                                speed = location.speed.toDouble()
                            ))
                    }
                    .subscribeOn(Schedulers.computation())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { }
            }
        }
    }

    private fun checkSpeed(context: Context, location: Location) {
        PreferenceManager.getDefaultSharedPreferences(context).let { pref ->
            if (!pref.getBoolean(SoundMeterSettings.SETTING_NOTIFY, true)) {
                return@let
            }
            val threshold = pref.getInt(SoundMeterSettings.SETTING_SPEED, resources.getInteger(R.integer.speed_default))
            if (location.speed > threshold) {
                // threshold reached
                (context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                        .notify(
                                NOTIFICATION_TAG_SPEED_THRESHOLD, COUNT++,
                                NotificationCompat.Builder(context, SoundMeterApplication.NOTIFICATION_CHANNEL)
                                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                        .setContentTitle(
                                                getString(
                                                        R.string.notification_speed_title,
                                                        location.speed.toString(),
                                                        threshold.toString()
                                                )
                                        )
                                        .setContentIntent(getActivityPendingIntent())
                                        .build()
                        )
            }
        }
    }


}