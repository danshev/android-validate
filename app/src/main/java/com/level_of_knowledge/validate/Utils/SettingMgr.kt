package com.level_of_knowledge.validate.Utils

import android.content.Context
import java.io.File
import java.io.InputStream

object SettingMgr {
    var context : Context? = null

    private val PREF_NAME = "Configuration"

    fun  getValue(strKey : String) : String? {
        val sharedPreference = context?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return sharedPreference?.getString(strKey, null)
    }

    fun setValue(strKey: String, strValue : String) : Boolean {
        val sharedPreferences = context?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        if (sharedPreferences != null) {
            val edit = sharedPreferences!!.edit()

            edit.putString(strKey, strValue)
            edit.commit()
        }
        return false
    }


    fun getLocalKeySet() : List<String>? {
        val sharedPreferences = context?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        return sharedPreferences?.getStringSet("keychain", null)?.toList()
    }

    fun setLocalKeySet(keySet : List<String>) {
        val sharedPreferences = context?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        if (sharedPreferences != null) {
            val editor = sharedPreferences!!.edit()

            editor.putStringSet("keychain", keySet.toSet())
            editor.commit()
        }

    }
}