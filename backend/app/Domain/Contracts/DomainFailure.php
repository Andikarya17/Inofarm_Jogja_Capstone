<?php

namespace App\Domain\Contracts;

use Symfony\Component\HttpKernel\Exception\HttpException;

final class DomainFailure extends HttpException
{
    public function __construct(public readonly string $domainCode, string $message, int $status = 409,
        public readonly ?int $currentVersion = null, public readonly ?array $currentData = null)
    {
        parent::__construct($status, $message);
    }
}
