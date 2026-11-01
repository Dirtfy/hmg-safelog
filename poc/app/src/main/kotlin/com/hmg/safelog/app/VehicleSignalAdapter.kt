package com.hmg.safelog.app

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
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
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
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

        v.brake.registerBrakePedalStatus(object : BrakePedalStatusListener {
            override fun onBrakePedalStatusUpdated(pressed: Boolean?) {
                listener.onBrake(now(), pressed == true)
            }

            override fun onFailed(e: Exception) {
                Log.w(TAG, "brake listener failed", e)
            }
        })

        v.gear.registerGearSelection(object : GearSelectionListener {
            override fun onSelected(gear: VehicleGear?) {
                listener.onGear(now(), mapGear(gear))
            }

            override fun onFailed(e: Exception) {
                Log.w(TAG, "gear listener failed", e)
            }
        })

        // Row 1 left: driver seat airbag
        v.safety.registerSeatAirbagDeployed(
            SeatArea(SeatValue.ROW_1_LEFT),
            object : SeatAirbagDeployedListener {
                override fun onSeatAirbagDeployUpdated(deployed: Boolean?) {
                    if (deployed == true) listener.onAirbagDeployed(now())
                }

                override fun onFailed(e: Exception) {
                    Log.w(TAG, "airbag listener failed", e)
                }
            },
        )

        v.safety.registerTelltales(object : TelltaleInfoListener {
            override fun onTelltaleInfoUpdated(telltales: Telltales) {
                listener.onTelltales(now(), mapTelltales(telltales))
            }

            override fun onFailed(e: Exception) {
                Log.w(TAG, "telltale listener failed", e)
            }
        })

        startSpeed(listener)
    }

    private fun startSpeed(listener: SignalListener) {
        val c = Car.createCar(context)
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
        mgr.registerCallback(cb, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_FASTEST)
    }

    override fun stop() {
        speedCallback?.let { propertyManager?.unregisterCallback(it) }
        car?.disconnect()
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
    }
}
