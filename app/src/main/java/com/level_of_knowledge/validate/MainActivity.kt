package com.level_of_knowledge.validate

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import com.google.android.gms.vision.CameraSource
import com.google.android.gms.vision.Detector
import com.google.android.gms.vision.barcode.Barcode
import com.google.android.gms.vision.barcode.BarcodeDetector

class MainActivity : AppCompatActivity(), DigitalIDValidatorDelegate{
    val REQUEST_CAMERA = 1;

    val _tagProgressChange = "downloadProgressChange"
    val _tagRecProfImage = "didReceiveProfileImage"

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
        val cameraPreview = findViewById<SurfaceView>(R.id.camera_preview)

        val barcodeDetector = BarcodeDetector.Builder(this)
                .setBarcodeFormats(Barcode.QR_CODE)
                .build()

        if(!barcodeDetector.isOperational){
            Log.d("main", "Barcode detector not operational")
        }

        barcodeDetector.setProcessor(object : Detector.Processor<Barcode>{
            override fun receiveDetections(p0: Detector.Detections<Barcode>?) {
                val arr = p0!!.detectedItems

                if(arr == null){
                    System.out.println("arr is null")
                }

                if(arr.size() > 0){
                    for(i in 0..arr.size()) run {
                        Log.d("main", arr.toString())
                    }
                }
            }

            override fun release() {

            }
        })

        val cameraSource = CameraSource.Builder(this, barcodeDetector)
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
    }

    /*override fun handleResult(p0: Result?) {
        Log.d("QRCodeScanner", p0?.getText())
        Log.d("QRCodeScanner", p0?.getBarcodeFormat().toString())

        val fakeBarcode = Barcode();
        fakeBarcode.rawValue = p0?.text
        fakeBarcode.format = Barcode.QR_CODE

        val test = DigitalIDValidator.createInstance(applicationContext)
        SettingMgr.context = applicationContext
        test?.delegate = this

        val useOnlineValidation = findViewById<Switch>(R.id.switch_validation).isChecked

        if (test != null) {
            fakeBarcode.rawValue = "A000000248010001000202BF×Smith÷Patrick O'Neil÷'19 84 02 23'÷'20 16 11 21'÷'20 21 02 23'÷USA÷MA÷T1000924÷B;19910901;20350301;;;×1÷0068÷÷BRO÷÷÷900 State Street;;Duxbury;MA;02586;USA××××××××a078e190b1f210af38595b14e956420bb866fb79572a39789c9291fa62a2ebeb4dcc8389e7968acb1b7b422468319ede3d465edc6150272ef171609807199c81e02fd4a66c43c98acbe287c4a76c1a7121f895937e694e8451a2b5a215c7b1287882b14e41ac330b9e1d6ebdd40f47289e84a2bf15a6c232f4f1fcdbbf5a8ac40ad18f187b5fc97b8b7c6ee30bf07b3fc29da88ac39d4e5b2317f2768e4a0d671d5eaa365db11623b8ee1a7ff479baad916077c5d0f4769c32c0d4f209e66158614a30c8b5c6c54717baa992bee536f6db78f7468f6f1743c4df039df2ca7dae206a03e278c80c806797ede92b03a33e30cbe2e01b6ca3559bb1d04573c0f22a×¶"
            val validate1 = test.validate(fakeBarcode, usingValidationService = useOnlineValidation)
            Log.e("main", "primary QR result: ${validate1}")

            if(validate1.first != null && validate1.first == true){
                if (useOnlineValidation) {
                    test.performOnlineValidation { valid, reason ->
                        Log.e("main", "online validation result: ${valid}")
                    }
                } else {
                    fakeBarcode.rawValue = test.digitalWatermark.toString()
                    val validate2 = test.validate(fakeBarcode, usingValidationService = useOnlineValidation)
                    Log.e("main", "secondary QR result: ${validate2}")
                }
            }else{
                Log.e("Main", "Error: validation failure. Cannot proceed to check QR code.")
            }

        }
    }*/

}
