package com.inofarm.jogja.core.contracts

enum class RecordKind(val wire:String,val resource:String,val label:String,val financial:Boolean=false) {
    ACTIVITY("activity","activities","kegiatan"), HARVEST("harvest","harvests","panen"),
    EXPENSE("expense","expenses","pengeluaran",true), COST_ALLOCATION("cost_allocation","cost-allocations","alokasi biaya",true)
}
typealias OperationalKind = RecordKind
