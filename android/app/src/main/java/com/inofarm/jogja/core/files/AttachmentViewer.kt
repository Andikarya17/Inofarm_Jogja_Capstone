package com.inofarm.jogja.core.files

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AtomicFile
import androidx.core.content.FileProvider
import com.inofarm.jogja.core.contracts.*
import com.inofarm.jogja.core.database.LocalBlob
import com.inofarm.jogja.core.database.LocalFinanceStore
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.core.session.FinanceSessionVault
import java.io.File

/** Only a user-requested external preview creates a temporary plaintext copy. Drafts stay encrypted. */
class AttachmentViewer(private val context:Context,private val vault:FinanceSessionVault,private val store:LocalFinanceStore,private val files:EncryptedAttachmentStore) {
    private val root=File(context.cacheDir,"finance-attachment-views")
    @Synchronized fun prepare(session:FinanceSession,record:LocalRecord,blob:LocalBlob):Intent {
        check(vault.current?.scope==session.scope && vault.current?.role==session.role && !session.expired()) {"Masuk kembali sebelum membuka berkas di aplikasi lain."}
        require(record.kind in AttachmentContract.kinds && record.greenhouseId in store.visibleGreenhouses(session.scope.key))
        require(record.syncState !in setOf(SyncState.SCOPE_REVOKED,SyncState.EPOCH_REVIEW,SyncState.AUTH_REQUIRED)) {"Lampiran ditahan untuk tinjauan akses atau rekonsiliasi."}
        require(record.kind!="expense_attachment" || session.role==UserRole.MANAGER)
        val metadata=AttachmentBytes.parse(record.serverBody ?: record.body)
        require(blob.entityId==record.id && blob.state=="READY" && AttachmentBytes.parse(blob.body)==metadata)
        files.verify(session.scope.key,blob.id,metadata)
        val extension=when(metadata.mime) {"image/png" -> "png";"image/jpeg" -> "jpg";"application/pdf" -> "pdf";else -> error("Jenis berkas belum didukung.")}
        val directory=File(root,"${session.scope.key}/${record.kind}/${record.greenhouseId}")
        require(directory.canonicalPath.startsWith(root.canonicalPath+File.separator));check(directory.isDirectory || directory.mkdirs())
        val path=File(directory,"${blob.id}.$extension");val atomic=AtomicFile(path);val output=atomic.startWrite()
        try {files.open(session.scope.key,blob.id).use {it.copyTo(output)};atomic.finishWrite(output)} catch(error:Throwable) {atomic.failWrite(output);throw error}
        val uri=FileProvider.getUriForFile(context,context.packageName+".finance-files",path,metadata.name)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri,metadata.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply {clipData=ClipData.newRawUri("Lampiran Inofarm",uri)}
    }
    @Synchronized fun clearViews() {
        if(!root.isDirectory) return
        val prefix=root.canonicalPath+File.separator
        root.walkBottomUp().forEach {file ->
            if(file.isFile && file.canonicalPath.startsWith(prefix)) {
                val parts=file.relativeTo(root).invariantSeparatorsPath.split('/')
                val name=file.name.removeSuffix(".new").removeSuffix(".bak")
                if(parts.size==4 && Regex("[0-9a-f]{64}").matches(parts[0]) && parts[1] in AttachmentContract.kinds && runCatching {WireContract.uuid(parts[2]);WireContract.uuid(name.substringBeforeLast('.'))}.isSuccess && name.substringAfterLast('.') in setOf("png","jpg","pdf")) {
                    runCatching {context.revokeUriPermission(FileProvider.getUriForFile(context,context.packageName+".finance-files",File(file.parentFile,name)),Intent.FLAG_GRANT_READ_URI_PERMISSION)}
                    file.delete()
                }
            } else if(file.isDirectory && file.canonicalPath.startsWith(prefix) && file.list()?.isEmpty()==true) file.delete()
        }
        if(root.list()?.isEmpty()==true) root.delete()
    }
}
