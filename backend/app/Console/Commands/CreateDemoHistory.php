<?php

namespace App\Console\Commands;

use App\Support\DemoHistory;
use Carbon\CarbonImmutable;
use Illuminate\Console\Command;
use Throwable;

class CreateDemoHistory extends Command
{
    protected $signature = 'inofarm:demo-history {--as-of= : YYYY-MM-DD WIB; default hari ini, data 10 hari sebelumnya}';

    protected $description = 'Buat akun/tandon DEMO lokal terpisah, 30 catatan dan 30 telemetry sintetis, tanpa hardware.';

    public function handle(DemoHistory $demo): int
    {
        if (! app()->environment('local')) {
            $this->error('Command demo hanya untuk environment local.');

            return self::FAILURE;
        }
        $date = $this->option('as-of') ?: CarbonImmutable::now('Asia/Jakarta')->toDateString();
        if (! preg_match('/^\d{4}-\d{2}-\d{2}$/D', $date) || ! CarbonImmutable::hasFormatWithModifiers($date, 'Y-m-d')) {
            $this->error('Tanggal harus YYYY-MM-DD yang valid.');

            return self::FAILURE;
        }
        $path = base_path('../.runtime/demo-history-credentials.json');
        $file = @fopen($path, 'x');
        if (! $file) {
            $this->error('Folder .runtime harus ada dan file credential demo belum boleh ada. Tidak menimpa file existing.');

            return self::FAILURE;
        }
        try {
            $password = bin2hex(random_bytes(12));
            $credentials = json_encode(['email' => DemoHistory::EMAIL, 'password' => $password, 'purpose' => 'DEMO DUMMY lokal, bukan akun produksi'], JSON_PRETTY_PRINT | JSON_THROW_ON_ERROR);
            if (fwrite($file, $credentials) !== strlen($credentials) || ! fflush($file)) {
                throw new \RuntimeException('Gagal menyimpan credential demo.');
            }
            $summary = $demo->create(CarbonImmutable::parse($date, 'Asia/Jakarta'), $password);
            $this->line(json_encode($summary, JSON_PRETTY_PRINT | JSON_THROW_ON_ERROR));
            $this->info('Credential akun demo disimpan privat di .runtime/demo-history-credentials.json; tidak dicetak.');
        } catch (Throwable $error) {
            fclose($file);
            unlink($path);
            $this->error($error->getMessage());

            return self::FAILURE;
        }
        fclose($file);

        return self::SUCCESS;
    }
}
