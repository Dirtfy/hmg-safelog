package com.hmg.safelog.app

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SeatAirbagDeployedListener
import com.hmg.safelog.core.Gear
import com.hmg.safelog.core.SignalListener
import com.hmg.safelog.core.SignalSource
import com.hmg.safelog.core.TelltaleReading
import com.hmg.safelog.core.WarningTelltale

/**
 * Pleos Vehicle SDK (Brake / Gear / Safety) + AAOS CarPropertyManager (speed) -> core SignalSource.
 */
class VehicleSignalAdapter(private val context: Context) : SignalSource {
    private var vehicle: Vehicle? = null
    private var car: Car? = null
    private var propertyManager: CarPropertyManager? = null
    private var speedCallback: CarPropertyManager.CarPropertyEventCallback? = null

    private fun now() = System.currentTimeMillis()

    override fun start(listener: SignalListener) {
        val v = Vehicle(context)
        v.initialize()
        vehicle = v

        guarded("brake") { v.getBrake().registerBrakePedalStatus(object : BrakePedalStatusListener {
            override fun onBrakePedalStatusUpdated(pressed: Boolean?) {
                listener.onBrake(now(), pressed == true)
            }

            override fun onFailed(e: Exception) {
                Log.w(TAG, "brake listener failed", e)
            }
        }) }

        guarded("gear") { v.getGear().registerGearSelection(object : GearSelectionListener {
            override fun onSelected(gear: VehicleGear?) {
                listener.onGear(now(), mapGear(gear))
            }

            override fun onFailed(e: Exception) {
                Log.w(TAG, "gear listener failed", e)
            }
        }) }

        // Row 1 left: driver seat airbag
        guarded("airbag") { v.getSafety().registerSeatAirbagDeployed(
            SeatArea(SeatValue.ROW_1_LEFT),
            object : SeatAirbagDeployedListener {
                override fun onSeatAirbagDeployUpdated(deployed: Boolean?) {
                    if (deployed == true) listener.onAirbagDeployed(now())
                }

                override fun onFailed(e: Exception) {
                    Log.w(TAG, "airbag listener failed", e)
                }
            },
        ) }

        // registerTelltales needs pleos.car.permission.PRIVATE_CAR_SAFETY (not for third-party apps; found on the
        // Pleos Connect Emulator 2.1.0). getTelltalesInfo only needs CAR_SAFETY, so poll it once a second.
        pollTelltales(v, listener)

        guarded("speed") { startSpeed(listener) }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var telltalePoll: Runnable? = null
    private var telltaleWarned = false

    private fun pollTelltales(v: Vehicle, listener: SignalListener) {
        val r = object : Runnable {
            override fun run() {
                guarded("telltales") {
                    v.getSafety().getTelltalesInfo({ t ->
                        val tt: Telltales? = t
                        if (tt != null) listener.onTelltales(now(), mapTelltales(tt))
                    }, { e ->
                        if (!telltaleWarned) Log.w(TAG, "PERMISSION_DENIED telltales (getTelltalesInfo): $e")
                        telltaleWarned = true
                    })
                }
                handler.postDelayed(this, TELLTALE_POLL_MS)
            }
        }
        telltalePoll = r
        handler.post(r)
    }

    /** One missing permission must not take the whole app down; log it and keep the other signals. */
    private inline fun guarded(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: SecurityException) {
            Log.w(TAG, "PERMISSION_DENIED $what: ${e.message}")
        }
    }

    private var carThread: HandlerThread? = null

    private fun startSpeed(listener: SignalListener) {
        // Car callbacks default to the main thread; a busy UI thread delayed speed events by tens of seconds
        // on the Pleos emulator. Deliver them on their own thread instead.
        val t = HandlerThread("safelog-car").also { it.start() }
        carThread = t
        val c = Car.createCar(context, Handler(t.looper))
        car = c
        val mgr = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
        propertyManager = mgr
        val cb = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                val ms = (value.value as? Float) ?: return // PERF_VEHICLE_SPEED: m/s
                listener.onSpeed(now(), ms * 3.6)
            }

            override fun onErrorEvent(propId: Int, zone: Int) {
                Log.w(TAG, "speed error prop=$propId zone=$zone")
            }
        }
        speedCallback = cb
        val ok = mgr.registerCallback(cb, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_FASTEST)
        Log.i(TAG, "speed callback registered=$ok")
    }

    override fun stop() {
        telltalePoll?.let { handler.removeCallbacks(it) }
        speedCallback?.let { propertyManager?.unregisterCallback(it) }
        car?.disconnect()
        carThread?.quitSafely()
        vehicle?.release()
        speedCallback = null; propertyManager = null; car = null; vehicle = null
    }

    private fun mapGear(g: VehicleGear?): Gear = when (g) {
        VehicleGear.GEAR_PARK -> Gear.PARK
        VehicleGear.GEAR_REVERSE -> Gear.REVERSE
        VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
        VehicleGear.GEAR_DRIVE, VehicleGear.GEAR_1, VehicleGear.GEAR_2, VehicleGear.GEAR_3,
        VehicleGear.GEAR_4, VehicleGear.GEAR_5, VehicleGear.GEAR_6, VehicleGear.GEAR_7,
        VehicleGear.GEAR_8, VehicleGear.GEAR_9,
        -> Gear.DRIVE
        else -> Gear.UNKNOWN
    }

    private fun mapTelltales(t: Telltales): List<TelltaleReading> =
        t.telltales.mapNotNull { d ->
            val type = WarningTelltale.values().firstOrNull { it.name == d.type.name } ?: return@mapNotNull null
            TelltaleReading(type, d.state != TelltaleState.OFF)
        }

    private companion object {
        const val TAG = "SafeLog"
        const val TELLTALE_POLL_MS = 1_000L
    }
}
