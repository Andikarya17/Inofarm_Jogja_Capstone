<?php

namespace Tests\Unit;

use PHPUnit\Framework\Attributes\DataProvider;
use PHPUnit\Framework\TestCase;
use Symfony\Component\Mime\Exception\RfcComplianceException;
use Symfony\Component\Mime\Header\ParameterizedHeader;

class MimeHeaderSafetyTest extends TestCase
{
    public static function unsafeNames(): array
    {
        return [['filename'."\r\n".'X-Injected'], ['file name'], ['file:name']];
    }

    #[DataProvider('unsafeNames')]
    public function test_parameter_names_cannot_inject_or_break_mime_headers(string $name): void
    {
        $this->expectException(RfcComplianceException::class);
        new ParameterizedHeader('Content-Disposition', 'attachment', [$name => 'report.csv']);
    }

    public function test_valid_attachment_filename_remains_supported(): void
    {
        $header = new ParameterizedHeader('Content-Disposition', 'attachment', ['filename' => 'laporan.csv']);
        $this->assertStringContainsString('filename=laporan.csv', $header->getBodyAsString());
    }
}
