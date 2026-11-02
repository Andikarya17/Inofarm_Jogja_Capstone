package com.inofarm.jogja.core.contracts

/** Only uncommitted, explicitly reviewed corrections may replace a rejected immutable operation. */
object ReviewableRejections {
    fun mayReplace(kind:String,code:String?)=code=="validation_failed" || (kind=="harvest" && code=="harvest_in_use") ||
        (kind=="expense" && code=="expense_in_use") || (kind=="cost_allocation" && code=="allocation_exceeds_source") || (kind=="sale" && code=="harvest_exceeded") ||
        (kind=="asset" && code in setOf("asset_in_use","asset_exceeds_payment")) ||
        (kind=="depreciation_allocation" && code in setOf("depreciation_exceeds_budget","duplicate_asset_usage")) ||
        (kind=="season_report" && code in setOf("report_sources_changed","report_formula_changed","report_review_incomplete")) ||
        (kind=="investment_report" && code in setOf("report_sources_changed","report_formula_changed"))
}
