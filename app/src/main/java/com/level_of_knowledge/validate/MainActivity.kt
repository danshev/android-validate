package com.level_of_knowledge.validate

import android.support.v7.app.AppCompatActivity
import android.os.Bundle

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Here is some helper code to get you get testing.
        // There's currently an error with hexStringToByteArray()
        val test = DigitalIDValidator.createInstance(context = this)
        if (test != null) {
            var fakeBarcode = Barcode()
            fakeBarcode.format = Barcode.QR_CODE
            fakeBarcode.rawValue = "A000000248010001000202BF×Smith÷Patrick O'Neil÷'19 84 02 23'÷'20 16 11 21'÷'20 21 02 23'÷USA÷MA÷T1000924÷B;19910901;20350301;;;×1÷0068÷÷BRO÷÷÷900 State Street;;Duxbury;MA;02586;USA××××××××a078e190b1f210af38595b14e956420bb866fb79572a39789c9291fa62a2ebeb4dcc8389e7968acb1b7b422468319ede3d465edc6150272ef171609807199c81e02fd4a66c43c98acbe287c4a76c1a7121f895937e694e8451a2b5a215c7b1287882b14e41ac330b9e1d6ebdd40f47289e84a2bf15a6c232f4f1fcdbbf5a8ac40ad18f187b5fc97b8b7c6ee30bf07b3fc29da88ac39d4e5b2317f2768e4a0d671d5eaa365db11623b8ee1a7ff479baad916077c5d0f4769c32c0d4f209e66158614a30c8b5c6c54717baa992bee536f6db78f7468f6f1743c4df039df2ca7dae206a03e278c80c806797ede92b03a33e30cbe2e01b6ca3559bb1d04573c0f22a×¶"
            val validate = test.validate(fakeBarcode, usingValidationService = false)
        }
    }
}
