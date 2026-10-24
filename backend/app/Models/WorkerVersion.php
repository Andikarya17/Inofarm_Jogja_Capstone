<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class WorkerVersion extends Model
{
    protected $dateFormat = 'Y-m-d H:i:s.u';

    public $timestamps = false;

    protected $guarded = [];

    protected function casts(): array
    {
        return ['active' => 'boolean', 'effective_from' => 'immutable_datetime'];
    }
}
