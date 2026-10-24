<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class ShiftVersion extends Model
{
    protected $dateFormat = 'Y-m-d H:i:s.u';

    public $timestamps = false;

    protected $guarded = [];

    protected function casts(): array
    {
        return ['body' => 'array', 'effective_from' => 'immutable_datetime', 'effective_until' => 'immutable_datetime'];
    }
}
