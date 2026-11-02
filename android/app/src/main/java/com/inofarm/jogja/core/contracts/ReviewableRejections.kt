package com.inofarm.jogja.core.contracts

/** Only uncommitted, explicitly reviewed corrections may replace a rejected immutable operation. */
object ReviewableRejections {
    fun mayReplace(kind:String,code:String?)=code=="validation_failed" || (kind=="harvest" && code=="harvest_in_use")
}
