package com.example.gltfmodelviewer

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.GestureDetector
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.google.android.filament.View
import com.google.android.filament.utils.*
import java.nio.ByteBuffer

class MainActivity : Activity() {

    companion object {
        // Initialize Filament utilities
        init {
            Utils.init()
        }

        private const val TAG = "gltf-viewer"
    }

    private lateinit var titlebarHint: TextView
    private val singleTapListener = SingleTapListener()
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var scaleFactor = 1.0f
    private lateinit var singleTapDetector: GestureDetector
    private var statusToast: Toast? = null
    private var infoPopupWindow: PopupWindow? = null
    private var statusText: String? = null
    private lateinit var surfaceView: SurfaceView
    private lateinit var choreographer: Choreographer
    private val frameScheduler = FrameCallback()
    private lateinit var modelViewer: ModelViewer
    private val automation = AutomationEngine()
    private val viewerContent = AutomationEngine.ViewerContent()

    // Variable to keep track of the currently selected entity
    private var selectedRenderable: Int? = null

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
       val customView = CustomView(this)
        setContentView(customView)


        val bufferModel = assets.open("models/Tooth-3.glb").use { input ->
            val bytes = ByteArray(input.available())
            input.read(bytes)
            ByteBuffer.wrap(bytes)
        }
        customView.setModel(bufferModel)


        val ibl = "venetian_crossroads_2k"

        val lights = assets.open("envs/$ibl/${ibl}_ibl.ktx").use { input ->
            val bytes = ByteArray(input.available())
            input.read(bytes)
            ByteBuffer.wrap(bytes)
        }
        val skyBox = assets.open("envs/$ibl/${ibl}_skybox.ktx").use { input ->
            val bytes = ByteArray(input.available())
            input.read(bytes)
            ByteBuffer.wrap(bytes)
//            scene.skybox = KTX1Loader.createSkybox(engine, it)
        }

        customView.setLights(skyBox, lights)
        customView.setViewOptions()


        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun readAsset(assetName: String): ByteBuffer {
        return assets.open(assetName).use { input ->
            val bytes = ByteArray(input.available())
            input.read(bytes)
            ByteBuffer.wrap(bytes)
        }
    }

    private fun setStatusText(text: String) {
        runOnUiThread {
            if (statusToast == null || statusText != text) {
                statusText = text
                statusToast = Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT)
                statusToast!!.show()
            }
        }
    }

    override fun onResume() {
        Log.v(TAG, "onResume Lifecycle method called")
        super.onResume()
    }

    override fun onPause() {
        Log.v(TAG, "onPause Lifecycle method called")
        super.onPause()
    }

    override fun onDestroy() {
        Log.v(TAG, "onDestroy Lifecycle method called")
        super.onDestroy()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    inner class FrameCallback : Choreographer.FrameCallback {
        private val startTime = System.nanoTime()
        override fun doFrame(frameTimeNanos: Long) {
            choreographer.postFrameCallback(this)

            modelViewer.animator?.apply {
                if (animationCount > 0) {
                    val elapsedTimeSeconds = (frameTimeNanos - startTime) / 1_000_000_000.0
                    applyAnimation(0, elapsedTimeSeconds.toFloat())
                    updateBoneMatrices()
                }
            }

            modelViewer.render(frameTimeNanos)
        }
    }

    // Single-tap listener for picking
    inner class SingleTapListener : GestureDetector.SimpleOnGestureListener() {

        override fun onSingleTapUp(event: MotionEvent): Boolean {
            val x = event.x.toInt()
            val y = surfaceView.height - event.y.toInt()

            modelViewer.view.pick(x, y, surfaceView.handler) { result ->
                if (result.renderable == 0) {
                    Log.v(TAG, "No entity picked at ($x, $y)")
                    return@pick
                }

                val renderable = result.renderable
                Log.v(TAG, "Picked entity ID: $renderable")

                val engine = modelViewer.engine
                val rcm = engine.renderableManager

                if (selectedRenderable == renderable) {
                    // If the tapped entity is already selected, unselect it (reset to default color)
                    val ri = rcm.getInstance(renderable)
                    val primitiveCount = rcm.getPrimitiveCount(ri)
                    for (i in 0 until primitiveCount) {
                        val mi = rcm.getMaterialInstanceAt(ri, i)
                        // Reset to default color (white)
                        mi.setParameter("baseColorFactor", 1.0f, 1.0f, 1.0f, 1.0f)
                    }
                    // Deselect the entity
                    selectedRenderable = null
                    setStatusText("Deselected entity ID: $renderable")
                    infoPopupWindow?.dismiss()
                } else {
                    // Otherwise, select the new entity and apply color change

                    // Reset color of previously selected entity
                    selectedRenderable?.let { previousRenderable ->
                        if (rcm.hasComponent(previousRenderable)) {
                            val ri = rcm.getInstance(previousRenderable)
                            val primitiveCount = rcm.getPrimitiveCount(ri)
                            for (i in 0 until primitiveCount) {
                                val mi = rcm.getMaterialInstanceAt(ri, i)
                                // Reset to default color (white)
                                mi.setParameter("baseColorFactor", 1.0f, 1.0f, 1.0f, 1.0f)
                            }
                        }
                    }

                    // Set color of the newly selected entity
                    if (rcm.hasComponent(renderable)) {
                        val ri = rcm.getInstance(renderable)
                        val primitiveCount = rcm.getPrimitiveCount(ri)
                        for (i in 0 until primitiveCount) {
                            val mi = rcm.getMaterialInstanceAt(ri, i)
                            // Set to selected color (e.g., red)
                            mi.setParameter("baseColorFactor", 1.0f, 0.0f, 0.0f, 1.0f)
                        }
                    }

                    // Update the selectedRenderable
                    selectedRenderable = renderable

                    // Display the selected entity ID
                    setStatusText("Selected entity ID: $renderable")
                    showInfoPopup(renderable.toString())
                }
            }
            return super.onSingleTapUp(event)
        }
    }

    private fun showInfoPopup(entityName: String) {
        infoPopupWindow?.dismiss()

        val inflater = LayoutInflater.from(this)
        val popupView = inflater.inflate(R.layout.pop_up_info, null)
        val toothNameTextView = popupView.findViewById<TextView>(R.id.tooth_name)
        toothNameTextView.text = "Selected: $entityName"

        infoPopupWindow = PopupWindow(
            popupView,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        infoPopupWindow?.isOutsideTouchable = true
        infoPopupWindow?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        infoPopupWindow?.showAtLocation(surfaceView, Gravity.CENTER, 0, 0)
    }

}
