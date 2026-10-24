<?php

namespace App\Models;

use App\Services\WireFormat;
use Illuminate\Database\Eloquent\Model;

class ConfigVersion extends Model
{
    public function toArray(): array
    {
        $data = parent::toArray();
        $data['body'] = WireFormat::configuration($data['body']);

        return $data;
    }

    protected $dateFormat = 'Y-m-d H:i:s.u';

    public $timestamps = false;

    protected $guarded = [];

    protected function casts(): array
    {
        return ['body' => 'array', 'effective_from' => 'immutable_datetime'];
    }
}
