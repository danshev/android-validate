package com.level_of_knowledge.validate

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.vision.CameraSource
import com.google.android.gms.vision.Detector
import com.google.android.gms.vision.barcode.Barcode
import com.google.android.gms.vision.barcode.BarcodeDetector
import com.level_of_knowledge.validate.Utils.Constant
import com.level_of_knowledge.validate.Utils.SettingMgr
import java.util.*
import java.util.Calendar.*

@Suppress("DEPRECATED_IDENTITY_EQUALS")
class MainActivity : AppCompatActivity(), DigitalIDValidatorDelegate{
    val TAG = "Main"

    val REQUEST_CAMERA = 1

    lateinit var digIDVal: DigitalIDValidator
    var currentlyValidating = false

    val _tagProgressChange = "downloadProgressChange"
    val _tagRecProfImage = "didReceiveProfileImage"

    var displayingResult = false;

    lateinit var cameraSource : CameraSource
    lateinit var cameraPreview : SurfaceView

    // Delegate function handlers -->
    override fun downloadProgressDidChange(to: Float) {
        // Progress of the image download (for use with a visual progress indicator, for User feedback)
        Log.e(_tagProgressChange, "progress: ${to}")
    }

    override fun didReceiveProfileImage(profileImage: Bitmap) {
        Log.e(_tagRecProfImage, "downloaded image")
    }

    override fun validationServiceDidChange(available: Boolean) {
        Log.e(_tagProgressChange, "Online service available? ${available}")
    }
    // <-- Delegate function handlers

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // request camera permission
        digIDVal = DigitalIDValidator.createInstance(applicationContext)!!
        SettingMgr.context = applicationContext
        digIDVal?.delegate = this

        requestPermissions()
    }

    override fun onResume(){
        super.onResume()
    }

    override fun onStop(){
        super.onStop()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if(requestCode == REQUEST_CAMERA && grantResults[0] == PackageManager.PERMISSION_GRANTED){
            Log.d("TAG", "Camera Permission granted.")
        }
    }

    private fun requestPermissions(){
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                Toast.makeText(this, "Error: Unable to request camera permission.", Toast.LENGTH_SHORT).show()
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
            }
        }else{
            startCamera()
        }
    }

    private fun startCamera(){
        cameraPreview = findViewById<SurfaceView>(R.id.camera_preview)

        val barcodeDetector = BarcodeDetector.Builder(this)
                .setBarcodeFormats(Barcode.QR_CODE)
                .build()

        if(!barcodeDetector.isOperational){
            Log.d("main", "Barcode detector not operational")
        }

        barcodeDetector.setProcessor(object : Detector.Processor<Barcode>{
            override fun receiveDetections(detections: Detector.Detections<Barcode>) {
                val barcodes = detections.detectedItems
                if (barcodes.size() != 0 && !currentlyValidating) {
                    //Log.d("main", barcodes.valueAt(0).displayValue)
                    validateBardcode(barcodes.valueAt(0).displayValue)
                }
            }

            override fun release() {

            }
        })

        cameraSource = CameraSource.Builder(this, barcodeDetector)
                .setAutoFocusEnabled(true)
                .setFacing(CameraSource.CAMERA_FACING_BACK)
                .setRequestedFps(24f)
                .setRequestedPreviewSize(1600, 1024)
                .build()

        // attach callback methods to listeners

        cameraPreview.holder.addCallback(object : SurfaceHolder.Callback{
            override fun surfaceChanged(p0: SurfaceHolder?, p1: Int, p2: Int, p3: Int) {
            }

            override fun surfaceDestroyed(p0: SurfaceHolder?) {
                cameraSource.stop()
            }

            @SuppressLint("MissingPermission")
            override fun surfaceCreated(p0: SurfaceHolder?) {
                cameraSource.start(cameraPreview.holder)
            }
        })

        cameraPreview.invalidate()
    }

    fun validateBardcode(str : String) {
        currentlyValidating = true

        val barcode = Barcode()
        barcode.rawValue = str
        barcode.format = Barcode.QR_CODE

        if (digIDVal != null) {

            val useOnlineValidation = findViewById<Switch>(R.id.switch_validation).isChecked
            val (primaryQrResult, secondaryQrResult) = digIDVal.validate(barcode, usingValidationService = useOnlineValidation)

            primaryQrResult?.let {
                if (it) {
                    if (useOnlineValidation) {
                        digIDVal!!.performOnlineValidation { valid, reason ->
                            Log.e("main", "online validation result: ${valid}")
                            runOnUiThread {
                                if(valid)
                                    showResult(digIDVal.customerData)
                                else
                                    showResult(reason)
                            }
                        }
                    } else {
                        secondaryQrResult?.let {
                            if (it) {
                                Log.d("main", " **** ---> Both QR codes are VALID!")
                                runOnUiThread { showResult( digIDVal.customerData) }
                            } else {
                                Log.e("main", " **** ---> Secondary QR data is invalid")
                                runOnUiThread { showResult(Constant.configuration["invalid-id-error-message"]) }
                            }
                        }
                    }
                } else {
                    Log.e("main", " **** ---> Primary QR data does not have a valid digital signature")
                }
            }
        }
        currentlyValidating = false
    }

    fun showResult(result : String?){
        if(displayingResult)
            return

        displayingResult = true

        val view = View.inflate(this, R.layout.toast_layout, null)
        view.findViewById<TextView>(R.id.toast_text).text = result
        view.setBackgroundResource(R.drawable.background_error)

        val toast = Toast(this)
        toast.view = view
        toast.duration = Toast.LENGTH_LONG
        toast.show()

        val handler = Handler()
        handler.postDelayed({ displayingResult = false }, 2000)
    }

    @SuppressLint("MissingPermission")
    fun showResult(result : DigitalIDValidator.Customer){
        cameraSource.stop()

        val view = View.inflate(this, R.layout.toast_layout, null)
        val diffInYears = getDiffYears(result.dateOfBirth, Date(System.currentTimeMillis()))

        val str =
                result.givenNames + "\n" +
                result.familyName + "\n\n" +
                result.gender + " " + diffInYears

        view.findViewById<TextView>(R.id.toast_text).text = str
        view.setBackgroundResource(R.drawable.background_success)

        val alertDialog = AlertDialog.Builder(this).create()

        view.setOnClickListener { cameraSource.start(cameraPreview.holder); alertDialog.dismiss() }

        alertDialog.setView(view)
        alertDialog.setCancelable(false)
        alertDialog.window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT));
        alertDialog.window.setDimAmount(0.0f)
        alertDialog.show()
    }

    private fun getDiffYears(first: Date, last: Date): Int {
        val a = getCalendar(first)
        val b = getCalendar(last)
        var diff = b.get(YEAR) - a.get(YEAR)
        if (a.get(MONTH) > b.get(MONTH) || a.get(MONTH) === b.get(MONTH) && a.get(DATE) > b.get(DATE)) {
            diff--
        }
        return diff
    }

    private fun getCalendar(date: Date): Calendar {
        val cal = Calendar.getInstance(Locale.US)
        cal.time = date
        return cal
    }
}
