package com.intelligentapps.arspeedlimit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.mapbox.vision.VisionManager
import com.mapbox.vision.mobile.core.interfaces.VisionEventsListener
import com.mapbox.vision.mobile.core.models.AuthorizationStatus
import com.mapbox.vision.mobile.core.models.Camera
import com.mapbox.vision.mobile.core.models.Country
import com.mapbox.vision.mobile.core.models.FrameSegmentation
import com.mapbox.vision.mobile.core.models.classification.FrameSignClassifications
import com.mapbox.vision.mobile.core.models.detection.Detection
import com.mapbox.vision.mobile.core.models.detection.DetectionClass
import com.mapbox.vision.mobile.core.models.detection.FrameDetections
import com.mapbox.vision.mobile.core.models.frame.Image
import com.mapbox.vision.mobile.core.models.position.VehicleState
import com.mapbox.vision.mobile.core.models.road.RoadDescription
import com.mapbox.vision.mobile.core.models.world.WorldDescription
import com.mapbox.vision.mobile.core.utils.SystemInfoUtils
import com.mapbox.vision.performance.ModelPerformance
import com.mapbox.vision.performance.ModelPerformanceMode
import com.mapbox.vision.performance.ModelPerformanceRate
import com.mapbox.vision.safety.VisionSafetyManager
import com.mapbox.vision.safety.core.VisionSafetyListener
import com.mapbox.vision.safety.core.models.CollisionObject
import com.mapbox.vision.safety.core.models.RoadRestrictions
import com.mapbox.vision.view.VisionView
import java.nio.ByteBuffer
import kotlin.math.pow
import kotlin.math.sqrt

class MainActivity : BaseActivity() {

    companion object {
        private var TAG = MainActivity::class.java.simpleName
    }

    private var maxAllowedSpeed: Float = -1f
    private var visionManagerWasInit = false
    private lateinit var paint: Paint

    private lateinit var vision_view: VisionView
    private lateinit var speed_sign_view: ImageView
    private lateinit var speed_value_view: TextView
    private lateinit var speed_alert_view: FrameLayout
    private lateinit var collision_value_view: TextView
    private lateinit var collision_view: FrameLayout

    private lateinit var detections_view: ImageView

    private sealed class SpeedLimit(val imageResId: Int, val textColorId: Int) {
        class Overspeeding : SpeedLimit(R.drawable.speed_limit_overspeeding, android.R.color.white)
        class NormalSpeed : SpeedLimit(R.drawable.speed_limit_normal, android.R.color.black)
    }

    // this listener handles events from Vision SDK
    private val visionEventsListener = object : VisionEventsListener {

        override fun onAuthorizationStatusUpdated(authorizationStatus: AuthorizationStatus) {}

        override fun onFrameSegmentationUpdated(frameSegmentation: FrameSegmentation) {}

        override fun onFrameDetectionsUpdated(frameDetections: FrameDetections) {
            fun convertImageToBitmap(originalImage: Image): Bitmap {
                val bitmap = Bitmap.createBitmap(
                    originalImage.size.imageWidth,
                    originalImage.size.imageHeight,
                    Bitmap.Config.ARGB_8888
                )
                // prepare direct ByteBuffer that will hold camera frame data
                val buffer = ByteBuffer.allocateDirect(originalImage.sizeInBytes())
                // associate underlying native ByteBuffer with our buffer
                originalImage.copyPixels(buffer)
                buffer.rewind()
                // copy ByteBuffer to bitmap
                bitmap.copyPixelsFromBuffer(buffer)
                return bitmap
            }

            fun drawSingleDetection(canvas: Canvas, detection: Detection) {
                // first thing we get coordinates of bounding box
                val relativeBbox = detection.boundingBox
                // we need to transform them from relative (range [0, 1]) to absolute in terms of canvas(frame) size
                // we do not care about screen resolution at all - we will use cropCenter mode
                val absoluteBbox = RectF(
                    relativeBbox.left * canvas.width,
                    relativeBbox.top * canvas.height,
                    relativeBbox.right * canvas.width,
                    relativeBbox.bottom * canvas.height
                )
                // we want to draw circle bounds, we need radius and center for that
                val radius = sqrt(
                    (absoluteBbox.centerX() - absoluteBbox.left).pow(2) +
                            (absoluteBbox.centerY() - absoluteBbox.top).pow(2)
                )
                canvas.drawCircle(
                    absoluteBbox.centerX(),
                    absoluteBbox.centerY(),
                    radius,
                    paint
                )
            }

            //val frameBitmap = convertImageToBitmap(frameDetections.frame.image)
            // now we will draw current detections on canvas with frame bitmap
            //val canvas = Canvas(frameBitmap)
            for (detection in frameDetections.detections) {
                // we will draw only detected cars
                // and filter detections which we are not confident with
                if (detection.detectionClass == DetectionClass.Car && detection.confidence > 0.6) {
                    //drawSingleDetection(canvas, detection)
                    runOnUiThread {
                        detections_view.setImageResource(R.drawable.baseline_directions_car_orange_24)
                    }
                } else if (detection.detectionClass == DetectionClass.Person && detection.confidence > 0.6) {
                    //drawSingleDetection(canvas, detection)
                    runOnUiThread {
                        detections_view.setImageResource(R.drawable.baseline_directions_walk_24)
                    }
                } else if (detection.detectionClass == DetectionClass.Bicycle && detection.confidence > 0.6) {
                    //drawSingleDetection(canvas, detection)
                    runOnUiThread {
                        detections_view.setImageResource(R.drawable.baseline_two_wheeler_24)
                    }
                } else if (detection.detectionClass == DetectionClass.TrafficSign && detection.confidence > 0.6) {
                    //drawSingleDetection(canvas, detection)
                    runOnUiThread {
                        detections_view.setImageResource(R.drawable.baseline_alt_route_24)
                    }
                }else if (detection.detectionClass == DetectionClass.TrafficLight && detection.confidence > 0.6) {
                    //drawSingleDetection(canvas, detection)
                    runOnUiThread {
                        detections_view.setImageResource(R.drawable.baseline_traffic_24)
                    }
                }
            }

        }

        override fun onFrameSignClassificationsUpdated(frameSignClassifications: FrameSignClassifications) {}

        override fun onRoadDescriptionUpdated(roadDescription: RoadDescription) {}

        override fun onWorldDescriptionUpdated(worldDescription: WorldDescription) {}

        override fun onVehicleStateUpdated(vehicleState: VehicleState) {
            // do nothing if we did not find any speed limit signs
            if (maxAllowedSpeed == -1f) return

            // current speed of our car
            val mySpeed = vehicleState.speed
            val currentSpeedState = if (mySpeed > maxAllowedSpeed && maxAllowedSpeed > 0) {
                SpeedLimit.Overspeeding()
            } else {
                SpeedLimit.NormalSpeed()
            }
            // all VisionListener callbacks are executed on a background thread. Need switch to a main thread
            runOnUiThread {
                speed_sign_view.setImageResource(currentSpeedState.imageResId)
                speed_value_view.setTextColor(ContextCompat.getColor(this@MainActivity, currentSpeedState.textColorId))
            }
        }

        override fun onCameraUpdated(camera: Camera) {}

        override fun onCountryUpdated(country: Country) {}

        override fun onUpdateCompleted() {}
    }

    // this listener handles events from VisionSafety SDK
    private val visionSafetyListener = object : VisionSafetyListener {
        override fun onCollisionsUpdated(collisions: Array<CollisionObject>) {
            var collsion = collisions[0]
            var warning: String
            warning = collsion.toString()

            // all VisionListener callbacks are executed on a background thread. Need switch to a main thread
            runOnUiThread {
                collision_value_view.text = warning
            }

        }

        override fun onRoadRestrictionsUpdated(roadRestrictions: RoadRestrictions) {
            maxAllowedSpeed = roadRestrictions.speedLimits.car.max
            if (maxAllowedSpeed != -1f) {
                runOnUiThread {
                    // set speed limit
                    speed_value_view.text = maxAllowedSpeed.toInt().toString()
                    // start showing alert view
                    speed_alert_view.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onPermissionsGranted() {
        startVisionManager()
    }

    override fun initViews() {
        setContentView(R.layout.activity_main)

        vision_view = findViewById(R.id.vision_view)
        speed_sign_view = findViewById(R.id.speed_sign_view)
        speed_value_view = findViewById(R.id.speed_value_view)
        speed_alert_view = findViewById(R.id.speed_alert_view)

        collision_value_view = findViewById(R.id.collision_value_view)
        collision_view = findViewById(R.id.collision_view)

        detections_view = findViewById(R.id.detections_view)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!SystemInfoUtils.isVisionSupported()) {
            //VisionManager.init(this, getString(R.string.mapbox_access_token))
            Toast.makeText(this@MainActivity, "Vision SDK is not supported by the device", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this@MainActivity, "This devise is supporting Vision SDK", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStart() {
        super.onStart()
        startVisionManager()
    }

    override fun onStop() {
        super.onStop()
        stopVisionManager()
    }

    override fun onResume() {
        super.onResume()
        vision_view.onResume()
    }

    override fun onPause() {
        super.onPause()
        vision_view.onPause()
    }

    private fun startVisionManager() {
        try {
            if (!visionManagerWasInit) {
                VisionManager.create()

                /*
                VisionManager.setModelPerformance(
                    ModelPerformance.On(
                        ModelPerformanceMode.DYNAMIC,
                        ModelPerformanceRate.LOW
                    )
                )

                 */

                vision_view.setVisionManager(VisionManager)
                VisionManager.start()
                VisionManager.visionEventsListener = visionEventsListener

                VisionSafetyManager.create(VisionManager)
                VisionSafetyManager.visionSafetyListener = visionSafetyListener

                visionManagerWasInit = true
            }
        } catch (e:Exception){
            Log.d(TAG, e.message.toString())
        }
    }

    private fun stopVisionManager() {
        if (visionManagerWasInit) {
            VisionSafetyManager.destroy()

            VisionManager.stop()
            VisionManager.destroy()

            visionManagerWasInit = false
        }
    }
}