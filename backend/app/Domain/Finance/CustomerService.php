<?php

namespace App\Domain\Finance;

use App\Domain\Identity\EntityIdentity;
use App\Domain\Identity\GreenhouseAccess;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;

/**
 * Farm-wide buyer list (Inofarm 20 Nov 2026): consumers, importable from Excel, and partners. Buyers belong to no
 * greenhouse, so they never enter the change feed; every creation still writes an audit row.
 */
final class CustomerService
{
    public const MAX_IMPORT_ROWS = 1000;

    public function __construct(private GreenhouseAccess $access) {}

    public function search(User $actor, ?string $type, ?string $q, int $limit, ?string $cursor): array
    {
        $this->access->manager($actor);
        $query = DB::table('customers')->where('record_state', 'active')->when($type, fn ($b, $t) => $b->where('type', $t));
        if ($q !== null && trim($q) !== '') {
            $digits = preg_replace('/\D/', '', $q);
            $query->where(function ($b) use ($q, $digits) {
                $b->where('name', 'like', '%'.addcslashes(trim($q), '%_\\').'%');
                if (strlen($digits) >= 3) {
                    $b->orWhere('whatsapp', 'like', '%'.ltrim($digits, '0').'%');
                }
            });
        }
        $page = $query->orderBy('name')->orderBy('id')->cursorPaginate($limit, ['*'], 'cursor', $cursor);

        return ['data' => collect($page->items())->map(fn ($row) => $this->publicRow($row))->values(), 'next_cursor' => $page->nextCursor()?->encode()];
    }

    public function publicRow(object $row): array
    {
        $stats = DB::table('sales')->where('customer_id', $row->id)->where('record_state', 'active')
            ->selectRaw('COUNT(*) AS purchases, CAST(COALESCE(SUM(total_weight_kg), 0) AS DECIMAL(20,6)) AS kg, MAX(paid_at) AS last_paid')->first();

        return ['id' => $row->id, 'type' => $row->type, 'name' => $row->name, 'whatsapp' => $row->whatsapp, 'whatsapp_display' => $row->whatsapp_display,
            'source' => $row->source, 'version' => (int) $row->version, 'purchase_count' => (int) $stats->purchases, 'total_kg' => (string) $stats->kg,
            'last_purchase_at' => $stats->last_paid ? CarbonImmutable::parse($stats->last_paid, 'UTC')->toISOString() : null];
    }

    /** Previews or saves imported rows; rows with a WhatsApp number already on the list are skipped, never duplicated. */
    public function import(User $actor, string $type, array $rows, bool $dryRun): array
    {
        $this->access->manager($actor);

        return DB::transaction(function () use ($actor, $type, $rows, $dryRun) {
            DB::table('sync_state')->where('id', 1)->lockForUpdate()->first();
            $seen = [];
            $result = [];
            foreach ($rows as $row) {
                $name = trim((string) ($row['name'] ?? ''));
                $raw = $row['whatsapp'] ?? null;
                $number = WhatsappNumber::normalize($raw);
                if ($name === '') {
                    $result[] = ['row' => $row['row'], 'status' => 'invalid', 'customer_id' => null, 'reason' => 'Nama kosong.'];
                    continue;
                }
                if ($raw !== null && trim((string) $raw) !== '' && $number === null) {
                    $result[] = ['row' => $row['row'], 'status' => 'invalid', 'customer_id' => null, 'reason' => 'Nomor WhatsApp tidak valid. Kosongkan untuk mengimpor tanpa nomor.'];
                    continue;
                }
                // A row id from the client makes a retried import idempotent, also for rows without a number.
                $rowId = $row['id'] ?? null;
                $existing = $rowId !== null ? DB::table('customers')->where('id', $rowId)->value('id') : null;
                $existing ??= $number === null ? null : ($seen[$number] ?? DB::table('customers')->where('type', $type)->where('whatsapp', $number)->value('id'));
                if ($existing) {
                    $result[] = ['row' => $row['row'], 'status' => 'existing', 'customer_id' => $existing, 'reason' => null];
                    continue;
                }
                $id = $rowId ?? (string) Str::uuid();
                if (! $dryRun) {
                    $this->create($actor, $id, $type, $name, $number, 'import');
                }
                if ($number !== null) {
                    $seen[$number] = $id;
                }
                $result[] = ['row' => $row['row'], 'status' => 'created', 'customer_id' => $dryRun ? null : $id, 'reason' => null];
            }
            $count = fn ($status) => count(array_filter($result, fn ($r) => $r['status'] === $status));

            return ['summary' => ['created' => $count('created'), 'existing' => $count('existing'), 'invalid' => $count('invalid')], 'rows' => $result];
        });
    }

    /**
     * The buyer a sale names: an existing active one keeps its stored name; a new id creates the buyer in the sale's
     * transaction. Returns [id, type, name].
     */
    public function forSale(User $actor, array $customer): array
    {
        $row = DB::table('customers')->where('id', $customer['id'])->lockForUpdate()->first();
        if ($row) {
            abort_unless($row->record_state === 'active' && $row->type === $customer['type'], 422, 'Pembeli tidak aktif atau tipenya berbeda.');

            return [$row->id, $row->type, $row->name];
        }
        $number = WhatsappNumber::normalize($customer['whatsapp'] ?? null);
        abort_if(($customer['whatsapp'] ?? null) !== null && trim((string) $customer['whatsapp']) !== '' && $number === null, 422, 'Nomor WhatsApp pembeli tidak valid.');
        if ($number !== null && ($same = DB::table('customers')->where('type', $customer['type'])->where('whatsapp', $number)->first())) {
            return [$same->id, $same->type, $same->name];
        }
        $this->create($actor, $customer['id'], $customer['type'], trim($customer['name']), $number, 'manual');

        return [$customer['id'], $customer['type'], trim($customer['name'])];
    }

    private function create(User $actor, string $id, string $type, string $name, ?string $number, string $source): void
    {
        app(EntityIdentity::class)->claim('customer', $id);
        $now = now();
        DB::table('customers')->insert(['id' => $id, 'type' => $type, 'name' => $name, 'whatsapp' => $number,
            'whatsapp_display' => $number === null ? null : WhatsappNumber::display($number), 'source' => $source,
            'record_state' => 'active', 'version' => 1, 'created_at' => $now, 'updated_at' => $now]);
        DB::table('audit_logs')->insert(['actor_id' => $actor->id, 'operation_id' => (string) Str::uuid(), 'entity_type' => 'customer', 'entity_id' => $id,
            'action' => 'create', 'version' => 1, 'reason' => null,
            'changes' => json_encode(['before' => null, 'after' => ['id' => $id, 'type' => $type, 'name' => $name, 'source' => $source]], JSON_THROW_ON_ERROR), 'server_at' => $now]);
    }
}
