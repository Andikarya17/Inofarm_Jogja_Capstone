package com.inofarm.jogja.core.files

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.inofarm.jogja.core.contracts.WireContract
import com.inofarm.jogja.core.contracts.AttachmentBytes
import com.inofarm.jogja.core.contracts.AttachmentContract
import com.inofarm.jogja.core.contracts.LocalAttachmentFailure
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Immutable AES-GCM copies. Paths/AAD bind account+server and a separate blob UUID. */
class EncryptedAttachmentStore(context:Context,private val root:File=File(context.noBackupFilesDir,"finance-evidence-v2")) {
    private var cachedKey:SecretKey?=null
    @Synchronized private fun key():SecretKey {
        cachedKey?.let {return it}
        val alias="inofarm_finance_evidence_v2"
        val keys=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
        return ((keys.getKey(alias,null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()).also {cachedKey=it}
    }
    private fun file(scope:String,id:String):File {
        require(Regex("[0-9a-f]{64}").matches(scope));WireContract.uuid(id)
        return File(File(root,scope),"$id.blob")
    }
    private fun aad(scope:String,id:String)="$scope:$id".toByteArray(Charsets.UTF_8)
    fun exists(scope:String,id:String)=file(scope,id).isFile
    fun describe(scope:String,id:String,name:String):AttachmentBytes {
        val digest=MessageDigest.getInstance("SHA-256");val prefix=java.io.ByteArrayOutputStream(8);var count=0L
        open(scope,id).use {input -> val buffer=ByteArray(32768);while(true) {
            val size=input.read(buffer);if(size<0) break;count+=size;require(count<=AttachmentContract.MAX_SUPPORTED_BYTES)
            if(prefix.size()<8) prefix.write(buffer,0,minOf(8-prefix.size(),size));digest.update(buffer,0,size)
        }}
        return AttachmentContract.inspect(name,prefix.toByteArray(),count,digest.digest().joinToString("") {"%02x".format(it)})
    }
    @Synchronized fun copy(scope:String,id:String,name:String,input:InputStream,checkActive:()->Unit={}):AttachmentBytes {
        val path=file(scope,id);require(!path.exists()) {"Salinan berkas sudah ada. Identitas salinan tidak ditimpa."}
        check(path.parentFile!!.isDirectory || path.parentFile!!.mkdirs()) {"Penyimpanan privat belum tersedia."}
        val atomic=AtomicFile(path);val output=atomic.startWrite()
        try {
            val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.ENCRYPT_MODE,key());updateAAD(aad(scope,id))}
            output.write(byteArrayOf(73,70,66,1));output.write(cipher.iv.size);output.write(cipher.iv)
            val digest=MessageDigest.getInstance("SHA-256");val prefix=java.io.ByteArrayOutputStream(8)
            var total=0L;val buffer=ByteArray(32768)
            while(true) {
                checkActive();val read=input.read(buffer);if(read<0) break;if(read==0) continue
                total+=read;require(total<=AttachmentContract.MAX_SUPPORTED_BYTES) {"Berkas melebihi batas teknis32MiB. Batas produksi mengikuti server."}
                if(prefix.size()<8) prefix.write(buffer,0,minOf(8-prefix.size(),read))
                digest.update(buffer,0,read);cipher.update(buffer,0,read)?.let(output::write)
            }
            val sha=digest.digest().joinToString("") {"%02x".format(it)}
            val metadata=AttachmentContract.inspect(name,prefix.toByteArray(),total,sha)
            output.write(cipher.doFinal());atomic.finishWrite(output)
            return metadata
        } catch(error:Throwable) {atomic.failWrite(output);throw error}
    }
    fun open(scope:String,id:String):InputStream {
        val input=DataInputStream(AtomicFile(file(scope,id)).openRead())
        try {
            val header=ByteArray(4);input.readFully(header);require(header.contentEquals(byteArrayOf(73,70,66,1)))
            val length=input.readUnsignedByte();require(length==12);val iv=ByteArray(length);input.readFully(iv)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,iv));updateAAD(aad(scope,id))}
            return CipherInputStream(input,cipher)
        } catch(error:Exception) {input.close();throw LocalAttachmentFailure("Salinan terenkripsi tidak dapat dibuka. Pilih kembali atau tinjau berkas yang tertahan.")}
    }
    fun verify(scope:String,id:String,expected:AttachmentBytes) {
        try {
            val digest=MessageDigest.getInstance("SHA-256");var count=0L
            open(scope,id).use {input -> val buffer=ByteArray(32768);while(true) {val size=input.read(buffer);if(size<0) break;count+=size;require(count<=expected.bytes && count<=AttachmentContract.MAX_SUPPORTED_BYTES);digest.update(buffer,0,size)}}
            require(count==expected.bytes && digest.digest().joinToString("") {"%02x".format(it)}==expected.sha256)
        } catch(error:Exception) {throw LocalAttachmentFailure("Salinan berkas hilang, rusak, atau checksum berubah. Pengiriman ditahan; operasi dan draf tetap disimpan.")}
    }
}
