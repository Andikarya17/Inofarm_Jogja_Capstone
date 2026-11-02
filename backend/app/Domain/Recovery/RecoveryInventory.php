<?php

namespace App\Domain\Recovery;

use App\Domain\Contracts\CanonicalJson;
use Illuminate\Support\Facades\DB;
use PDO;

/** Lossless logical MySQL inventory. DDL is evidence, never restore instructions. */
final class RecoveryInventory
{
    public function tables(): array
    {
        DB::statement('SET SESSION information_schema_stats_expiry=0');
        $result = [];
        foreach (DB::select('SELECT TABLE_NAME AS name, ENGINE AS engine, AUTO_INCREMENT AS next_id FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE=\'BASE TABLE\' ORDER BY TABLE_NAME') as $row) {
            $name = $this->identifier($row->name);
            if ($row->engine !== 'InnoDB') {
                throw new \RuntimeException('Recovery requires transactional InnoDB tables.');
            }
            $ddl = array_values((array) DB::selectOne('SHOW CREATE TABLE `'.$name.'`'))[1];
            $ddl = preg_replace('/ AUTO_INCREMENT=\d+/', '', $ddl);
            $columns = array_map(fn ($c) => $c->name, DB::select('SELECT COLUMN_NAME AS name FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? ORDER BY ORDINAL_POSITION', [$name]));
            $pk = array_map(fn ($c) => $c->name, DB::select('SELECT COLUMN_NAME AS name FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND CONSTRAINT_NAME=\'PRIMARY\' ORDER BY ORDINAL_POSITION', [$name]));
            if ($pk === []) {
                throw new \RuntimeException('A primary key is required for deterministic recovery.');
            }
            $result[$name] = ['ddl' => $ddl, 'schema_hash' => hash('sha256', $ddl), 'columns' => $columns, 'pk' => $pk, 'next_id' => $row->next_id === null ? null : (string) $row->next_id];
        }

        return $result;
    }

    public function identifier(string $name): string
    {
        if (! preg_match('/^[a-zA-Z0-9_]+$/D', $name)) {
            throw new \RuntimeException('Unsupported database identifier.');
        }

        return $name;
    }

    public function rows(string $table, array $meta, ?callable $write = null): array
    {
        $pdo = DB::connection()->getPdo();
        $previous = PHP_VERSION_ID >= 80400 ? $pdo->getAttribute(PDO::ATTR_STRINGIFY_FETCHES) : (bool) (config('database.connections.mysql.options', [])[PDO::ATTR_STRINGIFY_FETCHES] ?? false);
        $pdo->setAttribute(PDO::ATTR_STRINGIFY_FETCHES, true);
        $hash = hash_init('sha256');
        $count = 0;
        $bytes = 0;
        try {
            $query = $pdo->query('SELECT * FROM `'.$this->identifier($table).'` ORDER BY '.implode(',', array_map(fn ($c) => '`'.$this->identifier($c).'`', $meta['pk'])));
            while ($row = $query->fetch(PDO::FETCH_ASSOC)) {
                $encoded = [];
                foreach ($meta['columns'] as $column) {
                    $encoded[$column] = $row[$column] === null ? null : base64_encode((string) $row[$column]);
                }
                $line = CanonicalJson::encode($encoded)."\n";
                hash_update($hash, $line);
                $bytes += strlen($line);
                $count++;
                if ($write !== null) {
                    $write($line);
                }
            }
            $query->closeCursor();
        } finally {
            $pdo->setAttribute(PDO::ATTR_STRINGIFY_FETCHES, $previous);
        }

        return ['rows' => $count, 'bytes' => $bytes, 'sha256' => hash_final($hash)];
    }

    public function totals(): array
    {
        $result = [];
        foreach (['expenses' => ['amount'], 'cost_allocations' => ['amount'], 'assets' => ['cost', 'residual'], 'depreciation_allocations' => ['amount'], 'sales' => ['total_amount', 'total_weight_kg', 'exact_total'], 'sale_items' => ['quantity_kg', 'exact_amount'], 'harvest_items' => ['weight_kg', 'sold_weight_kg'], 'investment_scenarios' => ['initial_outlay'], 'investment_cash_flow_versions' => ['amount']] as $table => $columns) {
            $known = array_column(DB::select('SHOW COLUMNS FROM `'.$table.'`'), 'Field');
            foreach ($columns as $column) {
                if (in_array($column, $known, true)) {
                    $result[$table.'.'.$column] = DB::table($table)->selectRaw('CAST(SUM(`'.$column.'`) AS CHAR) AS total')->value('total');
                }
            }
        }

        return $result;
    }

    public function assertRelations(): int
    {
        $constraints = [];
        foreach (DB::select('SELECT TABLE_NAME AS child, COLUMN_NAME AS col, REFERENCED_TABLE_NAME AS parent, REFERENCED_COLUMN_NAME AS ref, CONSTRAINT_NAME AS name FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL ORDER BY TABLE_NAME,CONSTRAINT_NAME,ORDINAL_POSITION') as $r) {
            $constraints[$r->child.'.'.$r->name][] = $r;
        }
        foreach ($constraints as $fields) {
            $r = $fields[0];
            $join = [];
            $nonnull = [];
            foreach ($fields as $f) {
                $join[] = 'c.`'.$this->identifier($f->col).'`=p.`'.$this->identifier($f->ref).'`';
                $nonnull[] = 'c.`'.$f->col.'` IS NOT NULL';
            }
            $sql = 'SELECT COUNT(*) AS n FROM `'.$this->identifier($r->child).'` c LEFT JOIN `'.$this->identifier($r->parent).'` p ON '.implode(' AND ', $join).' WHERE '.implode(' AND ', $nonnull).' AND p.`'.$r->ref.'` IS NULL';
            if ((int) DB::selectOne($sql)->n !== 0) {
                throw new \RuntimeException('Recovery relation verification failed.');
            }
        }

        return count($constraints);
    }
}
