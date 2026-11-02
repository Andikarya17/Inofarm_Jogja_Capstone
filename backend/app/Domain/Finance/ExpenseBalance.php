<?php

namespace App\Domain\Finance;

use App\Domain\Contracts\DomainFailure;
use Illuminate\Support\Facades\DB;

final class ExpenseBalance
{
    public function assertConsistent(object $expense): void
    {
        $sum = DB::table('cost_allocations')->where('expense_id', $expense->id)->where('record_state', 'active')->sum('amount');
        if (bccomp((string) $sum, $expense->allocated_amount, 2) !== 0) {
            throw new DomainFailure('expense_balance_mismatch', 'Saldo biaya tidak cocok dengan rincian aktif. Perlu rekonsiliasi pengelola sebelum perubahan.', 500);
        }
    }

    public function publicRow(object $expense): array
    {
        $this->assertConsistent($expense);
        $active = $expense->record_state === 'active' && $expense->payment_kind === 'operating';

        return ['id' => $expense->balance_id, 'expense_id' => $expense->id, 'greenhouse_id' => $expense->greenhouse_id,
            'version' => $expense->balance_version, 'expense_version' => $expense->version, 'active' => $active, 'currency' => 'IDR',
            'amount' => $expense->amount, 'allocated_amount' => $expense->allocated_amount,
            'available_amount' => $active ? bcsub($expense->amount, $expense->allocated_amount, 2) : '0.00'];
    }

    /** Only called inside the authorized mutation ledger. */
    public function publish(object $expense): void
    {
        if (DB::transactionLevel() < 1) {
            throw new \LogicException('Expense balance feed requires a transaction.');
        }
        $body = $this->publicRow($expense);
        DB::table('change_feed')->insert(['greenhouse_id' => $expense->greenhouse_id, 'audience' => 'manager',
            'entity_type' => 'expense_balance', 'entity_id' => $body['id'], 'version' => $body['version'],
            'tombstone' => ! $body['active'], 'body' => json_encode($body, JSON_THROW_ON_ERROR), 'committed_at' => now()]);
    }
}
