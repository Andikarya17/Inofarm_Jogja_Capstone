<?php

namespace App\Models;

use App\Services\WireFormat;
use Illuminate\Database\Eloquent\Model;

class Event extends Model
{
    public function toArray(): array
    {
        $data = parent::toArray();
        if ($data['validation_result'] !== null) {
            $data['validation_result']['configSnapshot'] = WireFormat::configuration($data['validation_result']['configSnapshot']);
        }

        return $data;
    }

    protected $dateFormat = 'Y-m-d H:i:s.u';

    public $timestamps = false;

    protected $guarded = [];

    protected $hidden = ['payload_hash'];

    protected function casts(): array
    {
        return ['body' => 'array', 'validation_result' => 'array', 'assignment' => 'array', 'clock_trusted' => 'boolean', 'source_at' => 'immutable_datetime', 'received_at' => 'immutable_datetime', 'manual_ppm' => 'float'];
    }
}
