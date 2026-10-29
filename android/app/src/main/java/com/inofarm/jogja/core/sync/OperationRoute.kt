package com.inofarm.jogja.core.sync

import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.contracts.WireContract
import com.inofarm.jogja.core.contracts.AttachmentContract

data class OperationRoute(val path:String,val method:String) {
    companion object {
        fun forOperation(kind:String,action:String,id:String,role:UserRole):OperationRoute {
            WireContract.uuid(id)
            if(kind in AttachmentContract.kinds) {
                require(kind!="expense_attachment" || role==UserRole.MANAGER) {"Bukti keuangan hanya untuk pengelola."}
                require(action in setOf("create","cancel")) {"Berkas immutable; pilih lampiran baru."}
                require(action!="cancel" || role==UserRole.MANAGER) {"Pembatalan lampiran hanya untuk pengelola."}
                return OperationRoute(if(action=="create") "attachments" else "attachments/$id/cancel","POST")
            }
            if(kind=="activity" && action in setOf("create","update","cancel")) return OperationRoute("sync/operations","POST")
            if(kind=="harvest" && action in setOf("create","update","cancel")) return when(action) {
                "create" -> OperationRoute("harvests","POST")
                "update" -> OperationRoute("harvests/$id","PATCH")
                else -> OperationRoute("harvests/$id/cancel","POST")
            }
            require(role==UserRole.MANAGER) {"Perubahan ini hanya untuk pengelola."}
            if(kind in setOf("expense","cost_allocation") && action in setOf("create","update","cancel")) {
                val financeResource=if(kind=="expense") "expenses" else "cost-allocations"
                return when(action) {"create" -> OperationRoute(financeResource,"POST");"update" -> OperationRoute("$financeResource/$id","PATCH");else -> OperationRoute("$financeResource/$id/cancel","POST")}
            }
            val resource=mapOf("greenhouses" to "greenhouses","seasons" to "seasons","planting_groups" to "planting-groups",
                "varieties" to "varieties","materials" to "materials","units" to "units","grades" to "grades",
                "expense_categories" to "expense-categories","greenhouse_assignments" to "assignments","schedules" to "schedules","targets" to "targets")[kind]
                ?: error("Jenis operasi belum didukung. Antrean dipertahankan.")
            return when {
                action=="create" -> OperationRoute(resource,"POST")
                action=="update" -> OperationRoute("$resource/$id","PATCH")
                kind=="seasons" && action in setOf("close","reopen") -> OperationRoute("$resource/$id/$action","POST")
                else -> error("Tindakan belum didukung. Antrean dipertahankan.")
            }
        }
    }
}
