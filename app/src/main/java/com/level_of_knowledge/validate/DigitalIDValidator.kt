package com.level_of_knowledge.validate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.android.extension.responseJson
import com.google.android.gms.vision.barcode.Barcode
import com.level_of_knowledge.validate.Utils.Constant
import com.level_of_knowledge.validate.Utils.SettingMgr
import java.io.File
import java.io.InputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.properties.Delegates


interface DigitalIDValidatorDelegate {
    fun downloadProgressDidChange(to: Float)
    fun didReceiveProfileImage(profileImage: Bitmap)
    fun validationServiceDidChange(available: Boolean)
}

class DigitalIDValidator private constructor(val context: Context){
    companion object {
        private var _instance : DigitalIDValidator? = null

        fun shared() : DigitalIDValidator? {
            return _instance
        }

        fun createInstance(context : Context) : DigitalIDValidator? {
            _instance = DigitalIDValidator(context)
            _instance?.syncKeys()
            return _instance
        }
    }

    val TAG = "DigitalIDVal"

    val configuration = Constant.configuration

    var delegate : DigitalIDValidatorDelegate? = null

    data class Address(val line1 : String,
                       val line2 : String?,
                       val city : String,
                       val jurisdiction : String,
                       val postalCode : String) {}

    val ISO_IEC_5218 = mapOf(0 to "Not known", 1 to "Male", 2 to "Female", 9 to "Not applicable")
    val ANSI_D20_79  = mapOf(
            "BLK" to "Black",
            "BLU" to "Blue",
            "BRO" to "Brown",
            "GRY" to "Gray",
            "GRN" to "Green",
            "HAZ" to "Hazel",
            "MAR" to "Maroon",
            "PNK" to "Pink",
            "DIC" to "Dichromatic",
            "UNK" to "Unknown")

    data class Customer(val familyName : String,
                        val givenNames: String,
                        val dateOfBirth : Date,
                        val dateOfIssue : Date,
                        val dateOfExpiration : Date,
                        val issuingCountry : String,
                        val issuingJurisdiction : String,
                        val identifier : String,
                        val classRestrictions : String,
                        val gender : String,
                        val height : String,
                        val eyeColor : String,
                        val address : Address)

    val dateFormatter = SimpleDateFormat("yyyyMMdd")

    val groupDelimiter = "×"
    val fieldDelimiter = "÷"
    val finalDelimiter = "¶"

    // Define group--field parameteres
    val expectedNumberOfGroups = 12
    val expectedNumberGroup1Fields = 9
    val expectedNumberGroup2Fields = 7
    val expectedNumberGroup10Fields = 1

    // Holder variables for working values
    var signatureVerified by Delegates.observable(false){
        prop, old, new->
        println("$old -> $new")
    }
    lateinit var assertionHash : String
    var digitalWatermark : Int = -1 //Check
    lateinit var customerData : Customer

    var serviceAvailable = false
    private var currentlyPosting = false

    fun validate(barcode: Barcode, usingValidationService : Boolean = true) : Pair<Boolean?, Boolean?> {
        if (barcode.format != Barcode.QR_CODE){
            Log.e(TAG, "Error: Barcode format invalid")
            return Pair(null, null)
        }

        // Get the data contained by the QR code and ensure it's string
        val dataString = barcode.rawValue

        // Parse the string with the expected "group delimiter"
        val groups = dataString.split(groupDelimiter, limit = expectedNumberOfGroups)

        // A properly-encoded, primary QR code will contain 12 groups (header + 11 data fields)
        if (groups.count() == 12) {
            //Extract gropups for convenience (currently, only groups 1 and 10 are used)
            val group1 = groups[1]
            val group2 = groups[2]
            val group10 = groups[10]

            //Build the assertion
            val assertedString = "$group1$groupDelimiter$group2"

            // Extract the signature
            val signature = group10

            //Attempt to validate the assertion with any of the keys in the keychain
            val keychainIds = SettingMgr.getLocalKeySet()
            if (keychainIds == null){
                Log.e(TAG, "Error: key chain IDs are null")
                return (Pair(null, null))
            }

            var validSignature = false
            for (keyId in keychainIds) {
                val storedKey = getKey(keyId.toInt())

                if (storedKey != null) {
                    val pubKey = loadPublicKey(storedKey)
                    validSignature = RSAVerify(assertedString, signature, pubKey)
                }

                if (validSignature)
                    break
            }

            if (validSignature) {
                signatureVerified = true
                assertionHash = MD5(assertedString)
                digitalWatermark = calculateDigitalWatermark(assertionHash)

                //Ensure "Group 1" is/was properly-encoded
                val group1fields = group1.split(fieldDelimiter, limit = expectedNumberGroup1Fields)

                if (group1fields.count() != expectedNumberGroup1Fields) {
                    Log.e(TAG, "Error: group 1 fields count unexpected result: " + group1fields.count())
                    return Pair(null, null)
                }

                //Ensure "Group 2" is/was properly - encoded
                val group2fields = group2.split(fieldDelimiter, limit = expectedNumberGroup2Fields)

                if (group2fields.count() != expectedNumberGroup2Fields) {
                    Log.e(TAG, "Error: group 2 fields count unexpected result: " + group2fields.count())
                    return  Pair(null, null)
                }

                // Format dates into a usable format (required because of the 'compact encoding' standard to which the ID adheres)
                val dateOfBirth = convertBCDdate(group1fields[2])
                val dateOfIssue = convertBCDdate(group1fields[3])
                val dateOfExpiration = convertBCDdate(group1fields[4])

                val address = group2fields[6]
                val addressPieces = address.split(";")
                val street2 = if (addressPieces[1] == "") null else addressPieces[1]

                val customerAddress = Address(addressPieces[0],
                        street2,
                        addressPieces[2],
                        addressPieces[3],
                        addressPieces[4])

                // Build return object
                customerData = Customer(
                        group1fields[0],
                        group1fields[1],
                        dateOfBirth,
                        dateOfIssue,
                        dateOfExpiration,
                        group1fields[5],
                        group1fields[6],
                        group1fields[7],
                        group1fields[8],
                        ISO_IEC_5218[group2fields[0].toInt()]!!,
                        group2fields[2],
                        ANSI_D20_79[group2fields[3]]!!,
                        customerAddress
                )

                return Pair(true, null)
            } else {
                return Pair(false, false)
            }
        }
        // The secondary QR code -- the "digital watermark" -- will contain only 1 field (an Integer value)
        else if (groups.count() == 1 && !usingValidationService && signatureVerified ) {

            if (groups.count() == 0) {
                clearExistingResults()
                return Pair(true, false)
            }

            val qrCodeIntegerValue = groups[0].toInt()
            if (digitalWatermark == qrCodeIntegerValue) {
                clearExistingResults()
                return Pair(true, true)
            } else {
                clearExistingResults()
                return Pair(true, false)
            }
        }

        return Pair(null, null)
    }

    fun performOnlineValidation(completion: (Boolean, String?) -> Unit) {
        if (!currentlyPosting) {
            currentlyPosting = true

            val serverErrorMsg = configuration["server-error-message"]
            val invalidMsg = configuration["invalid-id-error-message"]
            val endpoint = configuration["online-validation-endpoint"]

            Fuel.get("$endpoint$assertionHash").responseJson { request, response, result ->
                currentlyPosting = false
                Log.d(TAG, response.responseMessage)

                result.fold({d ->
                    Log.d(TAG, response.responseMessage)
                    val jsonData = d.obj()
                    fetchProfileImage(jsonData["serialNumber"].toString())

                    Log.d(TAG, jsonData.toString())

                    completion(true, null)
                }, { err ->
                    Log.d(TAG, response.responseMessage)

                    // TODO: determine if it is a server error or invalid ID

                    completion(false, invalidMsg)
                })
            }
        }
    }

    private fun clearExistingResults() {
        assertionHash  = ""
        digitalWatermark = 0
        signatureVerified = false
    }

//region Helper functions
    fun RSAVerify(plainText : String, signaturer : String, publicKey : PublicKey) :  Boolean  {
        val publicSignature = Signature.getInstance("SHA512withRSA")
        publicSignature.initVerify(publicKey)
        publicSignature.update(plainText.toByteArray())

        val signatureBytes = hexStringToByteArray(signaturer)

        try {
            return publicSignature.verify(signatureBytes)
        } catch (e : Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun MD5(str: String): String {
        try {
            val digest = MessageDigest.getInstance("MD5")
            digest.update(str.toByteArray())

            val messageDigest = digest.digest()

            //Create Hex String
            val hexString = StringBuilder()
            for (aMessageDigest in messageDigest) {
                val h = String.format("%02x", aMessageDigest)
                hexString.append(h)
            }
            return hexString.toString()
        } catch (e : Exception) {
            e.printStackTrace()
        }

        return ""
    }

    fun loadPublicKey(stored : String) : PublicKey {
        var editedKey = stored
        editedKey = editedKey.replace("-----BEGIN PUBLIC KEY-----\n", "")
        editedKey = editedKey.replace("-----END PUBLIC KEY-----", "")

        val data = Base64.decode(editedKey, Base64.DEFAULT)
        val spec = X509EncodedKeySpec(data)
        val fact = KeyFactory.getInstance("RSA")

        return fact.generatePublic(spec)
    }

    private fun hexStringToByteArray(hexString: String): ByteArray {
        val len = hexString.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hexString[i], 16) shl 4) + Character
                    .digit(hexString[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun calculateDigitalWatermark(fromHash: String): Int {

        val numberString = fromHash.replace(regex = Regex("[^0-9]"), replacement =  "")
        val sum = numberString.map { it.toString().toInt() }.sum()
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val month = calendar.get(Calendar.MONTH) + 1
        val year = calendar.get(Calendar.YEAR)

        return sum * hour * day * month * year
    }

    private fun convertBCDdate(dateString : String) : Date {
        val dateStr = dateString.replace(" ", "").replace("'","")
        return dateFormatter.parse(dateStr)
    }
    //endregion
    //region Key management
    private fun syncKeys() {
        Log.d("DigiID", "syncKeys has been called")
        val endpoint = configuration["sync-keys-endpoint"]!!
        SettingMgr.context = this.context // <---- might not be the best place for it, but it got this function working.
        Fuel.get(endpoint).responseJson { request, response, result ->
            result.fold({d ->
                delegate?.validationServiceDidChange(true)
                serviceAvailable = true

                val serverKeysJson = d.array()
                val serverKeys = ArrayList<Int>()

                for (i in 0..serverKeysJson.length() - 1) {
                    serverKeys.add(serverKeysJson.getInt(i))
                }

                val localKeys = SettingMgr.getLocalKeySet()
                var needKeyList = serverKeys.toList()
                if (localKeys != null) {
                    needKeyList = serverKeys.filter { s -> !localKeys.contains(s.toString()) }
                }

                for (key in needKeyList) {
                    fetchKey(key)
                }
            }, {err ->
                Log.e("syncKey", "${err}")
                serviceAvailable = false
                delegate?.validationServiceDidChange(false)
            })
        }
    }

    fun getKey(withId : Int) : String? {
        val file = File(context.filesDir, "$withId.pub.pem")
        if (file.exists()) {
            val inputStream : InputStream = file.inputStream()
            return inputStream.bufferedReader().use { it.readText() }
        }

        return null
    }

    private fun fetchKey(withId: Int) {
        val endpoint = configuration["sync-keys-endpoint"]!!

        Fuel.download(endpoint + "?$withId").destination { response, url ->
            File(context.filesDir, "$withId.pub.pem")
        }.response { request, response, result ->
            val (data, error) = result
            if (error != null) {
                print(error)
            } else {

                var localKeys : List<String> = SettingMgr.getLocalKeySet() ?: listOf()

                if ( !localKeys.contains("$withId") ) {
                    val mutableList : MutableList<String> = mutableListOf()
                    mutableList.addAll(localKeys)
                    mutableList.add("$withId")
                    mutableList.sort()

                    SettingMgr.setLocalKeySet(mutableList)
                }
            }
        }
    }

    private fun fetchProfileImage(serialNumber : String) {
        val endpoint = configuration["fetch-profile-image-endpoint"]

        Fuel.download(endpoint + serialNumber).destination { response, url ->
            File.createTempFile("profileImage", ".jpg")
        }.response { request, response, result ->
            val (data, error) = result
            if (error != null) {
                Log.e("fetchProfileImage", "error: ${error}")
            } else {
                result.fold({ bytes ->

                    Log.e("fetchProfileImage", "bytes --> ${bytes.indices}")
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                    // delegate?.didReceiveProfileImage(bmp)
                }, {err ->
                    Log.e("fetchProfileImage", "error: ${err}")
                })
            }
        }.progress { readBytes, totalBytes ->
            val fractionCompleted = readBytes.toFloat() / totalBytes.toFloat()
            this.delegate?.downloadProgressDidChange(to = fractionCompleted)
        }
    }

    //endregion
}
