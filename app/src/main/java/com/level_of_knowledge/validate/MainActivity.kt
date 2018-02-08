package com.level_of_knowledge.validate

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import com.level_of_knowledge.validate.Utils.SettingMgr

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val test = DigitalIDValidator.createInstance(applicationContext)
        SettingMgr.context = applicationContext
        
        // Here is some helper code to get you get testing.
        // There's currently an error with hexStringToByteArray()
        val testResult = test?.validate(null, false)
        print(testResult)
    }
}
