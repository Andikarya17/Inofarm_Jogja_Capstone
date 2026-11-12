package com.inofarm.jogja.core.contracts

import org.json.JSONObject

data class AttachmentBytes(val name:String,val mime:String,val bytes:Long,val sha256:String) {
    fun json()=JSONObject().put("original_name",name).put("mime",mime).put("bytes",bytes).put("sha256",sha256)
    companion object {
        fun parse(json:JSONObject)=AttachmentBytes(json.getString("original_name"),json.getString("mime"),json.getLong("bytes"),json.getString("sha256"))
    }
}

class LocalAttachmentFailure(message:String):Exception(message)

object AttachmentContract {
    const val MAX_SUPPORTED_BYTES=33554432L
    val kinds=setOf("activity_attachment","harvest_attachment","expense_attachment")
    val operationalKinds=setOf("activity_attachment","harvest_attachment")
    val supportedMimes=listOf("image/jpeg","image/png","application/pdf")
    fun inspect(name:String,prefix:ByteArray,bytes:Long,sha:String):AttachmentBytes {
        require(name.isNotBlank() && name.codePointCount(0,name.length)<=200 && name.none {it.code<32 || it.code==127 || it=='/' || it=='\\'}) {"Nama berkas maksimal 200 karakter dan tidak boleh memuat garis miring."}
        require(bytes in 1..MAX_SUPPORTED_BYTES) {"Berkas kosong atau lebih dari 32 MB."}
        val mime=when {
            prefix.take(8).toByteArray().contentEquals(byteArrayOf(137.toByte(),80,78,71,13,10,26,10)) -> "image/png"
            prefix.size>=3 && prefix[0]==255.toByte() && prefix[1]==216.toByte() && prefix[2]==255.toByte() -> "image/jpeg"
            prefix.size>=5 && prefix.copyOfRange(0,5).contentEquals("%PDF-".toByteArray(Charsets.US_ASCII)) -> "application/pdf"
            else -> error("Berkas harus berupa JPEG, PNG, atau PDF.")
        }
        val extension=name.substringAfterLast('.',"").lowercase(java.util.Locale.ROOT)
        require(extension in when(mime) {"image/png" -> setOf("png");"image/jpeg" -> setOf("jpg","jpeg");else -> setOf("pdf")}) {"Akhiran nama berkas harus sesuai jenisnya (.jpg, .png, atau .pdf)."}
        require(Regex("[0-9a-f]{64}").matches(sha))
        return AttachmentBytes(name,mime,bytes,sha)
    }
    fun validatePolicy(policy:JSONObject?,file:AttachmentBytes) {
        require(policy!=null && policy.optBoolean("configured")) {"Lampiran belum bisa dikirim. Berkas tetap tersimpan di perangkat."}
        require(policy.optBoolean("upload_allowed")) {policy.optJSONArray("reasons")?.let {reasons -> (0 until reasons.length()).joinToString("\n") {reasons.getString(it)}}?.ifBlank {null} ?: "Lampiran belum bisa ditambahkan ke catatan ini."}
        require(!policy.isNull("max_bytes") && !policy.isNull("max_files")) {"Batas lampiran belum lengkap. Perbarui batas lalu coba lagi."}
        require(file.bytes<=policy.getLong("max_bytes")) {"Berkas terlalu besar. Pilih berkas yang lebih kecil."}
        val mimes=policy.getJSONArray("allowed_mime_types")
        require((0 until mimes.length()).any {mimes.getString(it)==file.mime}) {"Jenis berkas ini tidak diizinkan."}
    }
}
