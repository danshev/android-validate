package com.level_of_knowledge.validate.Utils

object Constant {
    val configuration = mapOf<String, String>(
            "invalid-id-error-message" to "The Customer needs to retrieve an updated version of his / her Digital ID.",
            "iBeaconIdentifier" to "gov.dhs.digital-drivers-license",
            "iBeaconUUID" to "04CAB66E-7BFF-43EB-9E85-F2F3AC23DC75",
            "fetch-profile-image-endpoint" to "http://api.level-of-knowledge.com:2006/validate?serialNumber=",
            "online-validation-endpoint" to "http://api.level-of-knowledge.com:2006/validate?assertionHash=",
            "sync-keys-endpoint" to "http://api.level-of-knowledge.com:3000/public-keys",
            "server-error-message" to "We're sorry, a server error occurred. Please tap validate in offline mode."
    )
}