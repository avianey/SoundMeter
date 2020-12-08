package com.github.avianey.soundmeter

import android.annotation.SuppressLint
import android.graphics.*
import android.graphics.drawable.Drawable
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.github.avianey.soundmeter.LocationService.Companion.EMPTY_LOCATION
import com.github.avianey.soundmeter.LocationService.Companion.locationObservable
import com.github.avianey.soundmeter.SoundMeterApplication.Companion.serviceStateObservable
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers

class GraphFragment: Fragment() {

    private lateinit var graphDrawable: GraphDrawable

    private val serviceStateConsumer = Consumer<LocationService.State> {
        when (it) {
            LocationService.State.RUNNING -> {
                // new recording
                path = Path()
                graphDrawable.invalidateSelf()
            }
        }
    }
    private val locationConsumer = Consumer<Location> { location ->
        if (location === EMPTY_LOCATION) {
            return@Consumer
        }
        path?.let {
            if (it.isEmpty) {
                zero = location.time
                it.moveTo(0f, location.speed)
            } else {
                it.lineTo((location.time - zero).toFloat(), location.speed)
            }
            graphDrawable?.invalidateSelf()
        }
    }

    private var locationDisposable: Disposable? = null
    private var serviceStateDisposable: Disposable? = null

    private var path: Path? = null
    private var zero: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        graphDrawable = GraphDrawable()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?): View? {
        return ImageView(requireContext())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view as ImageView
        view.setImageDrawable(graphDrawable)
    }

    @SuppressLint("CheckResult")
    override fun onResume() {
        super.onResume()
        path = null
        Observable
                .fromCallable {
                    var p: Path? = null
                    SoundMeterApplication.db.locationDao().getAll().forEach { entity ->
                        if (p == null) {
                            p = Path().apply {
                                zero = entity.time
                                moveTo(0f, entity.speed.toFloat())
                            }
                        } else {
                            p!!.lineTo((entity.time - zero).toFloat(), entity.speed.toFloat())
                        }
                    }
                    p ?: Path()
                }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { p ->
                    path = p
                    graphDrawable.invalidateSelf()
                    locationDisposable = locationObservable.subscribe(locationConsumer)
                }
        serviceStateDisposable = serviceStateObservable.subscribe(serviceStateConsumer)
    }

    override fun onPause() {
        super.onPause()
        locationDisposable?.dispose()
        serviceStateDisposable?.dispose()
    }

    inner class GraphDrawable: Drawable() {

        private val pathInternal = Path()

        override fun draw(canvas: Canvas) {
            canvas.drawPath(pathInternal, Paint().apply {
                color = resources.getColor(R.color.purple_500)
                style = Paint.Style.STROKE
                strokeWidth = resources.getDimensionPixelSize(R.dimen.graph_stroke_width).toFloat()
            })
        }

        override fun setBounds(bounds: Rect) {
            this.setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
        }

        override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
            super.setBounds(left, top, right, bottom)
        }

        override fun invalidateSelf() {
            // TODO in computation thread
            if (path != null && bounds.width() > 0) {
                val m = Matrix()
                val tmp = RectF()
                path!!.computeBounds(tmp, true)
                if (m.setRectToRect(tmp, RectF(bounds), Matrix.ScaleToFit.FILL)) {
                    m.postScale(1f, -1f, bounds.centerX().toFloat(), bounds.centerY().toFloat())
                    path!!.transform(m, pathInternal)
                } else {
                    pathInternal.reset()
                }
            }
            super.invalidateSelf()
        }

        override fun setAlpha(alpha: Int) {}

        override fun setColorFilter(colorFilter: ColorFilter?) {}

        override fun getOpacity() = PixelFormat.UNKNOWN

    }


}