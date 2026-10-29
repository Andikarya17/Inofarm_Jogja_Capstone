<?php

namespace App\Http\Controllers\V2;

use App\Domain\Contracts\CanonicalJson;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Validator;
use Illuminate\Validation\ValidationException;
use InvalidArgumentException;

final class OperationInput
{
    public const UUID = 'regex:/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/D';

    public static function read(Request $request, string $type, string $action, array $payloadRules, ?string $id = null): array
    {
        $rules = ['operation' => 'required|array:schema_version,sync_epoch,operation_id,entity_id,entity_type,action,base_version,payload',
            'operation.schema_version' => 'required|in:2', 'operation.sync_epoch' => ['required', self::UUID],
            'operation.operation_id' => ['required', self::UUID], 'operation.entity_id' => ['required', self::UUID],
            'operation.entity_type' => 'required|in:'.$type, 'operation.action' => 'required|in:'.$action,
            'operation.base_version' => $action === 'create' ? 'present|prohibited' : ['required', 'integer', 'min:1', 'max:2147483647'],
            'operation.payload' => 'required|array:'.implode(',', array_keys($payloadRules))];
        foreach ($payloadRules as $key => $rule) {
            $rules['operation.payload.'.$key] = $rule;
        }
        $result = Validator::make(['operation' => $request->all()], $rules)->validate()['operation'];
        abort_if($result['schema_version'] !== '2' || ($id !== null && $result['entity_id'] !== $id), 422, 'Identitas atau versi schema tidak sesuai.');
        abort_if($action !== 'create' && ! is_int($result['base_version']), 422, 'base_version harus integer.');
        $result['base_version'] = $result['base_version'] ?? null;
        try {
            CanonicalJson::encode($result);
        } catch (InvalidArgumentException) {
            throw ValidationException::withMessages(['payload' => 'Angka desimal harus dikirim sebagai string.']);
        }

        return $result;
    }
}
