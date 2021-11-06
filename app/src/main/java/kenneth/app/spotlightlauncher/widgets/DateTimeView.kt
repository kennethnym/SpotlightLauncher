package kenneth.app.spotlightlauncher.widgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.bumptech.glide.Glide
import dagger.hilt.android.AndroidEntryPoint
import kenneth.app.spotlightlauncher.R
import kenneth.app.spotlightlauncher.api.OpenWeatherApi
import kenneth.app.spotlightlauncher.databinding.DateTimeViewBinding
import kenneth.app.spotlightlauncher.prefs.datetime.DateTimePreferenceManager
import kenneth.app.spotlightlauncher.utils.activity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.IllegalArgumentException
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class DateTimeView(context: Context, attrs: AttributeSet) :
    LinearLayout(context, attrs), LifecycleObserver {
    @Inject
    lateinit var locale: Locale

    @Inject
    lateinit var dateTimePreferenceManager: DateTimePreferenceManager

    @Inject
    lateinit var openWeatherApi: OpenWeatherApi

    private val timeTickIntentFilter = IntentFilter(Intent.ACTION_TIME_TICK)
    private val weatherApiScope = CoroutineScope(Dispatchers.IO)

    /**
     * A BroadcastReceiver that receives broadcast of Intent.ACTION_TIME_TICK.
     * Must register this receiver in activity, or the time will not update.
     */
    private val timeTickBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context != null && intent?.action == Intent.ACTION_TIME_TICK) {
                updateTime()
            }
        }
    }

    private val timeFormat
        get() = SimpleDateFormat(
            if (dateTimePreferenceManager.shouldUse24HrClock)
                "HH:mm"
            else "h:mm a",
            locale
        )

    private val dateFormat = SimpleDateFormat("MMM d", locale)

    private val binding: DateTimeViewBinding

    private val separator: TextView

    init {
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT,
            1f
        )
        gravity = Gravity.CENTER
        orientation = VERTICAL

        binding = DateTimeViewBinding.inflate(LayoutInflater.from(context), this, true)

        separator = binding.dateTimeWeatherSeparator

        updateTime()
        showWeather()
        activity?.lifecycle?.addObserver(this)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    private fun onResume() {
        registerTimeTickListener()
        updateTime()
        showWeather()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    private fun onPause() {
        unregisterTickTickListener()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        unregisterTickTickListener()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        registerTimeTickListener()
    }

    private fun registerTimeTickListener() {
        context.registerReceiver(timeTickBroadcastReceiver, timeTickIntentFilter)
    }

    private fun unregisterTickTickListener() {
        try {
            context.unregisterReceiver(timeTickBroadcastReceiver)
        } catch (ex: IllegalArgumentException) {
        }
    }

    private fun showWeather() {
        if (dateTimePreferenceManager.shouldShowWeather) {
            loadWeather()
        } else {
            with(binding) {
                temp.isVisible = false
                weatherIcon.isVisible = false
                dateTimeWeatherSeparator.isVisible = false
            }
        }
    }

    private fun loadWeather() {
        weatherApiScope.launch {
            val weather = try {
                openWeatherApi.run {
                    latLong = dateTimePreferenceManager.weatherLocation
                    unit = dateTimePreferenceManager.weatherUnit
                    getCurrentWeather()
                }
            } catch (ex: Exception) {
                Log.e("hub", "$ex")
                return@launch
            }

            val isWeatherAvailable = weather != null

            activity?.runOnUiThread {
                binding.temp.isVisible = isWeatherAvailable
                separator.isVisible = isWeatherAvailable
                binding.weatherIcon.isVisible = isWeatherAvailable

                if (isWeatherAvailable) {
                    binding.temp.text = context.getString(
                        R.string.date_time_temperature_format,
                        weather!!.main.temp,
                        openWeatherApi.unit.symbol
                    )

                    Glide
                        .with(context)
                        .load(weather.weather[0].iconURL)
                        .into(binding.weatherIcon)

                    binding.weatherIcon.contentDescription = weather.weather[0].description
                    binding.dateTimeWeatherSeparator.isVisible = true
                }
            }
        }
    }

    private fun updateTime() {
        val currentTime = Calendar.getInstance().time

        binding.clock.text = timeFormat.format(currentTime)
        binding.date.text = dateFormat.format(currentTime)
    }
}
