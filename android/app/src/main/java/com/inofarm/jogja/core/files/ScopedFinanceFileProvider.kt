package com.inofarm.jogja.core.files

import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import com.inofarm.jogja.InofarmApplication
import com.inofarm.jogja.core.contracts.*
import java.io.File
import java.security.MessageDigest

/** URI grants alone never bypass the current account, cached role, GH scope or epoch hold. */
class ScopedFinanceFileProvider:FileProvider() {
    private fun authorize(uri:Uri) {
        val parts=uri.pathSegments;require(parts.size==5 && parts[0]=="finance_attachment_views") {"Pratinjau tidak tersedia."}
        val dependencies=(context!!.applicationContext as InofarmApplication).finance
        val session=dependencies.vault.restore() ?: throw SecurityException("Sesi tidak tersedia.")
        if(session.expired() || parts[1]!=session.scope.key || parts[2] !in AttachmentContract.kinds || (parts[2]=="expense_attachment" && session.role!=UserRole.MANAGER) || parts[3] !in dependencies.store.visibleGreenhouses(session.scope.key)) throw SecurityException("Akses pratinjau berubah.")
        val id=parts[4].substringBeforeLast('.');WireContract.uuid(id)
        val blob=dependencies.store.blob(session.scope.key,id) ?: throw SecurityException("Salinan tidak tersedia.")
        val record=dependencies.store.record(session.scope.key,blob.entityId) ?: throw SecurityException("Metadata tidak tersedia.")
        if(blob.state!="READY" || blob.kind!=parts[2] || blob.greenhouseId!=parts[3] || record.syncState in setOf(SyncState.SCOPE_REVOKED,SyncState.EPOCH_REVIEW,SyncState.AUTH_REQUIRED)) throw SecurityException("Salinan ditahan untuk tinjauan.")
        val expected=AttachmentBytes.parse(record.serverBody ?: record.body)
        if(AttachmentBytes.parse(blob.body)!=expected) throw SecurityException("Identitas salinan berubah.")
        val root=File(context!!.cacheDir,"finance-attachment-views");val file=File(root,parts.drop(1).joinToString("/"))
        if(!file.canonicalPath.startsWith(root.canonicalPath+File.separator) || !file.isFile || file.length()!=expected.bytes) throw SecurityException("Pratinjau belum lengkap.")
        val digest=MessageDigest.getInstance("SHA-256");file.inputStream().use {input -> val buffer=ByteArray(32768);while(true) {val count=input.read(buffer);if(count<0) break;digest.update(buffer,0,count)}}
        if(digest.digest().joinToString("") {"%02x".format(it)}!=expected.sha256) throw SecurityException("Checksum pratinjau berubah.")
    }
    override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor? {if(mode!="r") throw SecurityException("Pratinjau hanya baca.");authorize(uri);return super.openFile(uri,mode)}
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor {authorize(uri);return super.query(uri,projection,selection,selectionArgs,sortOrder)}
    override fun getType(uri:Uri):String? {authorize(uri);return super.getType(uri)}
    override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?):Int=throw SecurityException("Pratinjau hanya baca.")
}
