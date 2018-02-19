package com.level_of_knowledge.validate

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.MediaPlayer
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.github.lzyzsd.circleprogress.DonutProgress
import com.google.android.gms.vision.CameraSource
import com.google.android.gms.vision.Detector
import com.google.android.gms.vision.barcode.Barcode
import com.google.android.gms.vision.barcode.BarcodeDetector
import com.level_of_knowledge.validate.Utils.SettingMgr
import java.util.*
import java.util.Calendar.*

@Suppress("DEPRECATED_IDENTITY_EQUALS")
class MainActivity : AppCompatActivity(), DigitalIDValidatorDelegate{
    val TAG = "Main"

    val REQUEST_CAMERA = 1

    lateinit var digIDVal: DigitalIDValidator
    private lateinit var mp: MediaPlayer

    var currentlyValidating = false

    val _tagProgressChange = "downloadProgressChange"
    val _tagRecProfImage = "didReceiveProfileImage"

    var displayingResult = false;

    lateinit var cameraSource : CameraSource
    lateinit var cameraPreview : SurfaceView

    // Delegate function handlers -->
    override fun downloadProgressDidChange(to: Float) {
        val progressDialog = findViewById<View>(R.id.progress_dialog)
        val donutProgress = progressDialog.findViewById<DonutProgress>(R.id.donut_progress)
        donutProgress.progress = to
    }

    override fun didReceiveProfileImage(profileImage: Bitmap) {
        displayImage(profileImage)
    }

    override fun validationServiceDidChange(available: Boolean) {
        if (!available) {
            val onlineValidationSwitch = findViewById<Switch>(R.id.switch_validation)
            onlineValidationSwitch.isEnabled = false
            onlineValidationSwitch.setChecked(false)
            onlineValidationSwitch.setText("Validation service unavailable")
        }
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

    override fun onDestroy() {
        super.onDestroy()
        if(mp != null){
            mp.release()
        }
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
                            runOnUiThread { this.showResult(valid, reason) }
                        }
                    } else {
                        secondaryQrResult?.let {
                            if (it) {
                                runOnUiThread { showResult(true) }
                            } else {
                                runOnUiThread { showResult(false, "The secondary QR code contained invalid data") }
                            }
                        }
                    }
                } else {
                    runOnUiThread { showResult(false, "Primary QR data does not have a valid digital signature") }
                }
            }
        }
        currentlyValidating = false
    }

    @SuppressLint("MissingPermission")
    fun showResult(isValid: Boolean, reason: String? = null){
        cameraSource.stop()

        if(displayingResult)
            return;

        displayingResult = true;

        val view = View.inflate(this, R.layout.toast_layout, null)

        var info = reason
        if (isValid) {
            // Prep media player to play success sound
            mp = MediaPlayer.create (this, R.raw.validate_success)

            // Set green background
            view.setBackgroundResource(R.drawable.background_success)
            val diffInYears = getDiffYears(digIDVal.customerData.dateOfBirth, Date(System.currentTimeMillis()))
            info = digIDVal.customerData.familyName + "\n" +
                    digIDVal.customerData.givenNames + "\n\n" +
                    digIDVal.customerData.gender + " " + diffInYears

            // Show download progress indicator, if using online validation service
            if (findViewById<Switch>(R.id.switch_validation).isChecked)
                displayProgressDialog(true)

        } else {
            // Prep media player to play failure sound
            mp = MediaPlayer.create (this, R.raw.validate_failure)

            // Set red background
            view.setBackgroundResource(R.drawable.background_error)
        }

        view.findViewById<TextView>(R.id.toast_text).text = info
        val alertDialog = AlertDialog.Builder(this).create()
        alertDialog.setView(view)
        alertDialog.setCancelable(false)
        alertDialog.window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        alertDialog.window.setDimAmount(0.0f)
        alertDialog.window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        view.setOnClickListener {
            cameraSource.start(cameraPreview.holder)
            displayingResult = false
            mp.release()
            alertDialog.dismiss()
            displayProgressDialog(false)
        }

        mp.start()
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

    private fun displayProgressDialog(display : Boolean){
        val progressDialog = findViewById<View>(R.id.progress_dialog)
        val donutProgress = progressDialog.findViewById<DonutProgress>(R.id.donut_progress)
        val image = progressDialog.findViewById<ImageView>(R.id.image)

        if(display){
            progressDialog.visibility = View.VISIBLE
        }else{
            progressDialog.visibility = View.GONE
            donutProgress.visibility = View.VISIBLE
            image.visibility = View.GONE
        }
    }

    private fun displayImage(bmp : Bitmap){
        val progressDialog = findViewById<View>(R.id.progress_dialog)
        val donutProgress = progressDialog.findViewById<DonutProgress>(R.id.donut_progress)
        val image = progressDialog.findViewById<ImageView>(R.id.image)

        donutProgress.visibility = View.GONE
        image.visibility = View.VISIBLE

        image.setImageBitmap(bmp)
    }
}
