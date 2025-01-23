package com.example.gltfmodelviewer

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.Choreographer
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.LinearLayout
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import com.google.android.filament.View
import com.google.android.filament.utils.AutomationEngine
import com.google.android.filament.utils.KTX1Loader
import com.google.android.filament.utils.ModelViewer
import java.nio.ByteBuffer


@RequiresApi(Build.VERSION_CODES.Q)
class CustomView : LinearLayout, LifecycleObserver {

    val TAG = "CustomView"
    private lateinit var surfaceView: SurfaceView
    private lateinit var choreographer: Choreographer
    private val frameScheduler = FrameCallback()
    private lateinit var modelViewer: ModelViewer
    private val automation = AutomationEngine()
    private val viewerContent = AutomationEngine.ViewerContent()

    constructor(context: Context?) : super(context) {
        init(context)
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init(context)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(context)
    }

    constructor(
        context: Context?,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        init(context)
    }


    private fun init(context: Context?) {
        val inflated = LayoutInflater.from(context).inflate(R.layout.custom_view, this, true)
        val surfaceView = inflated.findViewById<SurfaceView>(R.id.main_sv);

        choreographer = Choreographer.getInstance()
        modelViewer = ModelViewer(surfaceView)
        viewerContent.view = modelViewer.view
        viewerContent.sunlight = modelViewer.light
        viewerContent.lightManager = modelViewer.engine.lightManager
        viewerContent.scene = modelViewer.scene
        viewerContent.renderer = modelViewer.renderer

        choreographer.postFrameCallback(frameScheduler)

        //callbacks
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                Log.d(
                    TAG,
                    "SurfaceCallback: 1 : surfaceCreated: Let ModelViewer handle attaching internally."
                )
                // No reflection needed here. ModelViewer’s UiHelper will detect the new surface.
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
                Log.d(
                    TAG,
                    "SurfaceCallback: 2 : surfaceChanged: updating viewport to ($width, $height)"
                )
                // Optionally set the viewport: modelViewer.view.viewport = IntRect(0, 0, width, height)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                Log.d(
                    TAG,
                    "SurfaceCallback: 3 : surfaceDestroyed: Let ModelViewer handle detaching internally."
                )
                // No reflection needed. Don’t destroy the engine here.
            }
        })

        (context as Activity).registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(p0: Activity, p1: Bundle?) {
                Log.e(TAG, "onActivityCreated: ")
            }

            override fun onActivityStarted(p0: Activity) {
                Log.e(TAG, "onActivityStarted: ")
            }

            override fun onActivityResumed(p0: Activity) {
                Log.e(TAG, "onActivityResumed: ")
                choreographer.postFrameCallback(frameScheduler)
            }

            override fun onActivityPaused(p0: Activity) {
                Log.e(TAG, "onActivityPaused: ")
                choreographer.removeFrameCallback(frameScheduler)
            }

            override fun onActivityStopped(p0: Activity) {
                Log.e(TAG, "onActivityStopped: ")
            }

            override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {
                Log.e(TAG, "onActivitySaveInstanceState: ")
            }

            override fun onActivityDestroyed(p0: Activity) {
                Log.e(TAG, "onActivityDestroyed: ")
                choreographer.removeFrameCallback(frameScheduler)
                modelViewer.destroyModel()
            }
        })
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onResume() {
        // Handle onStart lifecycle event
        println("CustomView is in ON_START")
        choreographer.postFrameCallback(frameScheduler)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun onPause() {
        // Handle onStop lifecycle event
        println("CustomView is in ON_STOP")
        choreographer.removeFrameCallback(frameScheduler)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        // Handle onDestroy lifecycle event
        println("CustomView is in ON_DESTROY")
        choreographer.removeFrameCallback(frameScheduler)
        modelViewer.destroyModel()
    }

    class CustomViewLifecycleObserver(
        val choreographer: Choreographer,
        val frameScheduler: FrameCallback,
        val modelViewer: ModelViewer
    ) : LifecycleEventObserver {

        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    Log.d("CustomViewLifecycleObserver", "ON_CREATE")
                }

                Lifecycle.Event.ON_START -> {
                    Log.d("CustomViewLifecycleObserver", "ON_START")
                }

                Lifecycle.Event.ON_RESUME -> {
                    Log.d("CustomViewLifecycleObserver", "ON_RESUME")

                }

                Lifecycle.Event.ON_PAUSE -> {
                    Log.d("CustomViewLifecycleObserver", "ON_PAUSE")
                }

                Lifecycle.Event.ON_STOP -> {
                    Log.d("CustomViewLifecycleObserver", "ON_STOP")
                }

                Lifecycle.Event.ON_DESTROY -> {
                    Log.d("CustomViewLifecycleObserver", "ON_DESTROY")

                }

                else -> {
                    Log.d("CustomViewLifecycleObserver", "Unknown")
                }
            }
        }
    }

    fun setModel(buffer: ByteBuffer) {
        modelViewer.loadModelGlb(buffer)
        if (automation.viewerOptions.autoScaleEnabled) {
            modelViewer.transformToUnitCube()
        } else {
            modelViewer.clearRootTransform()
        }
    }


    fun setLights(skyBox: ByteBuffer, indirectLight: ByteBuffer) {
        val engine = modelViewer.engine
        val scene = modelViewer.scene
        val ibl = "venetian_crossroads_2k"
        scene.indirectLight = KTX1Loader.createIndirectLight(engine, indirectLight)
        scene.indirectLight!!.intensity = 30_000.0f
        viewerContent.indirectLight = scene.indirectLight
        scene.skybox = KTX1Loader.createSkybox(engine, skyBox)
    }

    fun setViewOptions() {
        val view = modelViewer.view

        // Set render quality options
        view.renderQuality = view.renderQuality.apply {
            hdrColorBuffer = View.QualityLevel.MEDIUM
        }

        // Enable dynamic resolution
        view.dynamicResolutionOptions = view.dynamicResolutionOptions.apply {
            enabled = true
            quality = View.QualityLevel.MEDIUM
        }

        // Enable MSAA
        view.multiSampleAntiAliasingOptions = view.multiSampleAntiAliasingOptions.apply {
            enabled = true
        }

        // Enable FXAA
        view.antiAliasing = View.AntiAliasing.FXAA

        // Enable ambient occlusion
        view.ambientOcclusionOptions = view.ambientOcclusionOptions.apply {
            enabled = true
        }

        // Enable bloom
        view.bloomOptions = view.bloomOptions.apply {
            enabled = true
        }
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

}
