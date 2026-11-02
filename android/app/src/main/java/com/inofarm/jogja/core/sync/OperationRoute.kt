package com.inofarm.jogja.core.sync

import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.contracts.WireContract

data class OperationRoute(val path:String,val method:String) {
    companion object {
        fun forOperation(kind:String,action:String,id:String,role:UserRole):OperationRoute {
            WireContract.uuid(id)
            if(kind=="activity" && action in setOf("create","update","cancel")) return OperationRoute("sync/operations","POST")
            require(role==UserRole.MANAGER) {"Perubahan ini hanya untuk pengelola."}
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
