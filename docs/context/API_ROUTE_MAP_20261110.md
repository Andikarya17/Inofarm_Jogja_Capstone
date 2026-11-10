# Peta route API aktual, 10 November 2026

Baseline `9a1613e`, prefix `/api/v2`. Sebanyak 127 operasi OpenAPI dicocokkan melalui router Laravel dengan UUID sintetis tanpa menjalankan controller atau mengirim mutasi. Generic route diekspansi menurut constraint resource; HEAD otomatis tidak dihitung. Route match bukan bukti payload, authorization atau hasil SQL.

Runtime memiliki 153 kombinasi method/path setelah ekspansi. OpenAPI memiliki 127 operasi, atau 128 setelah format ekspor pdf/csv diekspansi. Ada 25 operasi runtime belum tercantum di OpenAPI. Seluruh operasi OpenAPI memiliki route; tidak ditemukan salah arah pada controller hasil pencocokan. Daftar berikut memetakan operasi yang dipublikasikan ke controller sebenarnya.

| Method | Path | Controller |
|---|---|---|
| POST | `/auth/login` | `AuthController@login` |
| POST | `/auth/logout` | `AuthController@logout` |
| GET | `/me` | `AuthController@me` |
| GET | `/greenhouses` | `ContextController@greenhouses` |
| POST | `/greenhouses` | `MasterController@store` |
| GET | `/greenhouses/{id}` | `ContextController@greenhouse` |
| PATCH | `/greenhouses/{id}` | `MasterController@update` |
| GET | `/seasons` | `MasterController@index` |
| POST | `/seasons` | `MasterController@store` |
| GET | `/planting-groups` | `MasterController@index` |
| POST | `/planting-groups` | `MasterController@store` |
| GET | `/sync/status` | `SyncStatusController` |
| POST | `/sync/operations` | `ActivityController@sync` |
| GET | `/sync/changes` | `ChangeFeedController` |
| GET | `/activity-reviews` | `ActivityReviewController@index` |
| POST | `/activity-reviews` | `ActivityReviewController@store` |
| GET | `/activity-reviews/recorders` | `ActivityReviewController@recorders` |
| GET | `/activities/{id}/verification` | `ActivityReviewController@show` |
| GET | `/activities` | `ActivityController@index` |
| POST | `/activities` | `ActivityController@store` |
| GET | `/activities/{id}` | `ActivityController@show` |
| PATCH | `/activities/{id}` | `ActivityController@update` |
| POST | `/varieties` | `MasterController@store` |
| GET | `/varieties` | `MasterController@index` |
| PATCH | `/varieties/{id}` | `MasterController@update` |
| POST | `/grades` | `MasterController@store` |
| GET | `/grades` | `MasterController@index` |
| PATCH | `/grades/{id}` | `MasterController@update` |
| POST | `/units` | `MasterController@store` |
| GET | `/units` | `MasterController@index` |
| PATCH | `/units/{id}` | `MasterController@update` |
| POST | `/materials` | `MasterController@store` |
| GET | `/materials` | `MasterController@index` |
| PATCH | `/materials/{id}` | `MasterController@update` |
| POST | `/expense-categories` | `MasterController@store` |
| GET | `/expense-categories` | `MasterController@index` |
| PATCH | `/expense-categories/{id}` | `MasterController@update` |
| PATCH | `/seasons/{id}` | `MasterController@update` |
| GET | `/planting-groups/{id}/harvest-progress` | `HarvestController@progress` |
| POST | `/planting-groups/{id}/harvest-completion` | `HarvestController@completion` |
| PATCH | `/planting-groups/{id}` | `MasterController@update` |
| POST | `/assignments` | `MasterController@store` |
| GET | `/assignments` | `MasterController@index` |
| PATCH | `/assignments/{id}` | `MasterController@update` |
| POST | `/seasons/{id}/close` | `MasterController@transition` |
| POST | `/seasons/{id}/reopen` | `MasterController@transition` |
| POST | `/schedules` | `ReferenceController@store` |
| GET | `/schedules` | `ReferenceController@index` |
| PATCH | `/schedules/{id}` | `ReferenceController@update` |
| POST | `/targets` | `ReferenceController@store` |
| GET | `/targets` | `ReferenceController@index` |
| PATCH | `/targets/{id}` | `ReferenceController@update` |
| POST | `/activities/{id}/cancel` | `ActivityController@cancel` |
| GET | `/activities/{id}/audit` | `ActivityController@audit` |
| GET | `/harvests` | `HarvestController@index` |
| POST | `/harvests` | `HarvestController@store` |
| GET | `/harvests/{id}` | `HarvestController@show` |
| PATCH | `/harvests/{id}` | `HarvestController@update` |
| POST | `/harvests/{id}/cancel` | `HarvestController@cancel` |
| GET | `/harvests/{id}/audit` | `HarvestController@audit` |
| GET | `/harvests/{id}/availability` | `HarvestController@availability` |
| GET | `/expenses` | `ExpenseController@index` |
| POST | `/expenses` | `ExpenseController@store` |
| GET | `/expenses/{id}` | `ExpenseController@show` |
| PATCH | `/expenses/{id}` | `ExpenseController@update` |
| POST | `/expenses/{id}/cancel` | `ExpenseController@cancel` |
| GET | `/expenses/{id}/audit` | `ExpenseController@audit` |
| GET | `/cost-allocations` | `ExpenseController@index` |
| POST | `/cost-allocations` | `ExpenseController@store` |
| GET | `/cost-allocations/{id}` | `ExpenseController@show` |
| PATCH | `/cost-allocations/{id}` | `ExpenseController@update` |
| POST | `/cost-allocations/{id}/cancel` | `ExpenseController@cancel` |
| GET | `/cost-allocations/{id}/audit` | `ExpenseController@audit` |
| GET | `/expenses/{id}/availability` | `ExpenseController@availability` |
| GET | `/attachments/policy` | `AttachmentController@policy` |
| GET | `/attachments` | `AttachmentController@index` |
| POST | `/attachments` | `AttachmentController@store` |
| GET | `/attachments/{id}` | `AttachmentController@show` |
| GET | `/attachments/{id}/content` | `AttachmentController@content` |
| POST | `/attachments/{id}/cancel` | `AttachmentController@cancel` |
| GET | `/attachments/{id}/audit` | `AttachmentController@audit` |
| GET | `/sales` | `SaleController@index` |
| POST | `/sales` | `SaleController@store` |
| GET | `/sales/{id}` | `SaleController@show` |
| PATCH | `/sales/{id}` | `SaleController@update` |
| POST | `/sales/{id}/cancel` | `SaleController@cancel` |
| GET | `/sales/{id}/audit` | `SaleController@audit` |
| GET | `/assets` | `AssetController@index` |
| POST | `/assets` | `AssetController@store` |
| GET | `/assets/{id}` | `AssetController@show` |
| PATCH | `/assets/{id}` | `AssetController@update` |
| POST | `/assets/{id}/cancel` | `AssetController@cancel` |
| GET | `/assets/{id}/audit` | `AssetController@audit` |
| GET | `/depreciation-allocations` | `AssetController@index` |
| POST | `/depreciation-allocations` | `AssetController@store` |
| GET | `/depreciation-allocations/{id}` | `AssetController@show` |
| PATCH | `/depreciation-allocations/{id}` | `AssetController@update` |
| POST | `/depreciation-allocations/{id}/cancel` | `AssetController@cancel` |
| GET | `/depreciation-allocations/{id}/audit` | `AssetController@audit` |
| POST | `/season-reports/preview` | `SeasonReportController@preview` |
| GET | `/season-reports/preview/sources` | `SeasonReportController@previewSources` |
| GET | `/season-reports` | `SeasonReportController@index` |
| POST | `/season-reports` | `SeasonReportController@store` |
| GET | `/reports/{id}` | `SeasonReportController@show` |
| GET | `/reports/{id}/sources` | `SeasonReportController@sources` |
| GET | `/investment-scenarios` | `InvestmentScenarioController@index` |
| POST | `/investment-scenarios` | `InvestmentScenarioController@store` |
| GET | `/investment-scenarios/{id}` | `InvestmentScenarioController@show` |
| PATCH | `/investment-scenarios/{id}` | `InvestmentScenarioController@update` |
| POST | `/investment-scenarios/{id}/cancel` | `InvestmentScenarioController@cancel` |
| GET | `/investment-scenarios/{id}/audit` | `InvestmentScenarioController@audit` |
| POST | `/investment-reports/preview` | `InvestmentReportController@preview` |
| GET | `/investment-reports` | `InvestmentReportController@index` |
| POST | `/investment-reports` | `InvestmentReportController@store` |
| GET | `/investment-reports/{id}` | `InvestmentReportController@show` |
| GET | `/investment-reports/{id}/sources` | `InvestmentReportController@sources` |
| GET | `/report-exports/{id}/{format}` | `ReportExportController` |
| GET | `/recovery/policy` | `RecoveryController@policy` |
| GET | `/recovery/backups` | `RecoveryController@index` |
| POST | `/recovery/backups` | `RecoveryController@store` |
| GET | `/recovery/backups/{id}` | `RecoveryController@show` |
| POST | `/recovery/backups/{id}/validate` | `RecoveryController@validatePackage` |
| GET | `/recovery/restores` | `RecoveryController@restores` |
| POST | `/sync/reconcile` | `ReceiptReviewController` |
| GET | `/measurements/latest` | `ActivityController@latestMeasurement` |
| GET | `/manager-home` | `ManagerHomeController` |
| POST | `/greenhouses/{id}/running-period` | `MasterController@designatePeriod` |

## Route runtime yang belum tercantum di OpenAPI

| Method | Path |
|---|---|
| GET | `/assignments/{id}` |
| GET | `/assignments/{id}/audit` |
| GET | `/expense-categories/{id}` |
| GET | `/expense-categories/{id}/audit` |
| GET | `/grades/{id}` |
| GET | `/grades/{id}/audit` |
| GET | `/greenhouses/{id}/audit` |
| GET | `/materials/{id}` |
| GET | `/materials/{id}/audit` |
| GET | `/planting-groups/{id}` |
| GET | `/planting-groups/{id}/audit` |
| GET | `/schedules/{id}` |
| GET | `/schedules/{id}/versions` |
| GET | `/seasons/{id}` |
| GET | `/seasons/{id}/audit` |
| GET | `/targets/{id}` |
| GET | `/targets/{id}/versions` |
| GET | `/units/{id}` |
| GET | `/units/{id}/audit` |
| GET | `/users` |
| GET | `/varieties/{id}` |
| GET | `/varieties/{id}/audit` |
| PATCH | `/users/{id}` |
| POST | `/users` |
| POST | `/users/assignment-candidate` |

## Batas schema respons

Operasi berikut memiliki respons sukses tanpa content/schema (logout dapat memang tidak memerlukan body). Jangan memakai OpenAPI sendirian untuk menghasilkan seluruh DTO client.

| Method | Path |
|---|---|
| GET | `/assignments` |
| GET | `/expense-categories` |
| GET | `/grades` |
| GET | `/materials` |
| GET | `/measurements/latest` |
| GET | `/schedules` |
| GET | `/targets` |
| GET | `/units` |
| GET | `/varieties` |
| PATCH | `/assignments/{id}` |
| PATCH | `/expense-categories/{id}` |
| PATCH | `/grades/{id}` |
| PATCH | `/greenhouses/{id}` |
| PATCH | `/materials/{id}` |
| PATCH | `/planting-groups/{id}` |
| PATCH | `/schedules/{id}` |
| PATCH | `/seasons/{id}` |
| PATCH | `/targets/{id}` |
| PATCH | `/units/{id}` |
| PATCH | `/varieties/{id}` |
| POST | `/assignments` |
| POST | `/auth/logout` |
| POST | `/expense-categories` |
| POST | `/grades` |
| POST | `/greenhouses` |
| POST | `/materials` |
| POST | `/planting-groups` |
| POST | `/schedules` |
| POST | `/seasons` |
| POST | `/seasons/{id}/close` |
| POST | `/seasons/{id}/reopen` |
| POST | `/targets` |
| POST | `/units` |
| POST | `/varieties` |

Total 34 operasi memiliki setidaknya satu respons sukses tanpa content/schema. Schema generik yang masih berupa object juga memerlukan pengetatan terpisah. Audit ini mendokumentasikan gap, tidak mengubah OpenAPI atau perilaku runtime.
