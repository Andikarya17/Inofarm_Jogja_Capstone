<?php

namespace App\Domain\Finance;

use RuntimeException;
use XMLWriter;
use ZipArchive;

/**
 * Minimal .xlsx writer without a dependency: inline strings, numbers as numeric cells.
 * A string that is a plain decimal is written as a number; null cells are left out so blank stays blank.
 */
final class SeasonWorkbook
{
    /** @param list<array{name: string, rows: list<list<string|int|float|null>>}> $sheets */
    public static function render(array $sheets): string
    {
        if ($sheets === []) {
            throw new RuntimeException('Workbook needs at least one sheet.');
        }
        $path = tempnam(sys_get_temp_dir(), 'xlsx');
        try {
            $zip = new ZipArchive;
            if ($zip->open($path, ZipArchive::CREATE | ZipArchive::OVERWRITE) !== true) {
                throw new RuntimeException('Cannot create workbook.');
            }
            $zip->addFromString('[Content_Types].xml', self::contentTypes(count($sheets)));
            $zip->addFromString('_rels/.rels', self::xml('Relationships', 'http://schemas.openxmlformats.org/package/2006/relationships', function (XMLWriter $w) {
                self::rel($w, 'rId1', 'http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument', 'xl/workbook.xml');
            }));
            $zip->addFromString('xl/workbook.xml', self::workbookXml($sheets));
            $zip->addFromString('xl/_rels/workbook.xml.rels', self::xml('Relationships', 'http://schemas.openxmlformats.org/package/2006/relationships', function (XMLWriter $w) use ($sheets) {
                foreach ($sheets as $i => $_) {
                    self::rel($w, 'rId'.($i + 1), 'http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet', 'worksheets/sheet'.($i + 1).'.xml');
                }
                self::rel($w, 'rId'.(count($sheets) + 1), 'http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles', 'styles.xml');
            }));
            $zip->addFromString('xl/styles.xml', self::stylesXml());
            foreach ($sheets as $i => $sheet) {
                $zip->addFromString('xl/worksheets/sheet'.($i + 1).'.xml', self::sheetXml($sheet['rows']));
            }
            $zip->close();
            $bytes = file_get_contents($path);
            if ($bytes === false) {
                throw new RuntimeException('Cannot read workbook.');
            }

            return $bytes;
        } finally {
            @unlink($path);
        }
    }

    private static function xml(string $root, string $ns, callable $body): string
    {
        $w = new XMLWriter;
        $w->openMemory();
        $w->startDocument('1.0', 'UTF-8', 'yes');
        $w->startElement($root);
        $w->writeAttribute('xmlns', $ns);
        $body($w);
        $w->endElement();

        return $w->outputMemory();
    }

    private static function rel(XMLWriter $w, string $id, string $type, string $target): void
    {
        $w->startElement('Relationship');
        $w->writeAttribute('Id', $id);
        $w->writeAttribute('Type', $type);
        $w->writeAttribute('Target', $target);
        $w->endElement();
    }

    private static function contentTypes(int $count): string
    {
        return self::xml('Types', 'http://schemas.openxmlformats.org/package/2006/content-types', function (XMLWriter $w) use ($count) {
            foreach ([['Default', 'Extension', 'rels', 'application/vnd.openxmlformats-package.relationships+xml'], ['Default', 'Extension', 'xml', 'application/xml']] as [$tag, $key, $value, $type]) {
                $w->startElement($tag);
                $w->writeAttribute($key, $value);
                $w->writeAttribute('ContentType', $type);
                $w->endElement();
            }
            $parts = ['/xl/workbook.xml' => 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml', '/xl/styles.xml' => 'application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml'];
            for ($i = 1; $i <= $count; $i++) {
                $parts['/xl/worksheets/sheet'.$i.'.xml'] = 'application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml';
            }
            foreach ($parts as $name => $type) {
                $w->startElement('Override');
                $w->writeAttribute('PartName', $name);
                $w->writeAttribute('ContentType', $type);
                $w->endElement();
            }
        });
    }

    private static function workbookXml(array $sheets): string
    {
        $w = new XMLWriter;
        $w->openMemory();
        $w->startDocument('1.0', 'UTF-8', 'yes');
        $w->startElement('workbook');
        $w->writeAttribute('xmlns', 'http://schemas.openxmlformats.org/spreadsheetml/2006/main');
        $w->writeAttribute('xmlns:r', 'http://schemas.openxmlformats.org/officeDocument/2006/relationships');
        $w->startElement('sheets');
        foreach ($sheets as $i => $sheet) {
            $w->startElement('sheet');
            $w->writeAttribute('name', $sheet['name']);
            $w->writeAttribute('sheetId', (string) ($i + 1));
            $w->writeAttribute('r:id', 'rId'.($i + 1));
            $w->endElement();
        }
        $w->endElement();
        $w->endElement();

        return $w->outputMemory();
    }

    private static function stylesXml(): string
    {
        return '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            .'<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
            .'<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>'
            .'<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>'
            .'<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>'
            .'<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>'
            .'<cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>'
            .'<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>'
            .'<xf numFmtId="3" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs>'
            .'</styleSheet>';
    }

    /** Column letters for a zero-based index. */
    private static function column(int $index): string
    {
        $name = '';
        for ($n = $index + 1; $n > 0; $n = intdiv($n - 1, 26)) {
            $name = chr(65 + ($n - 1) % 26).$name;
        }

        return $name;
    }

    private static function numeric(string|int|float $value): ?string
    {
        if (is_int($value)) {
            return (string) $value;
        }
        if (is_float($value)) {
            return is_finite($value) ? rtrim(rtrim(sprintf('%.6F', $value), '0'), '.') : null;
        }
        if (! preg_match('/^-?\d+(\.\d+)?$/', $value) || strlen($value) > 15 && ! str_contains($value, '.')) {
            return null;
        }
        $text = str_contains($value, '.') ? rtrim(rtrim($value, '0'), '.') : $value;

        return $text === '-0' || $text === '' ? '0' : $text;
    }

    private static function clean(string $text): string
    {
        return preg_replace('/[\x00-\x08\x0B\x0C\x0E-\x1F]/', '', $text) ?? '';
    }

    private static function sheetXml(array $rows): string
    {
        $w = new XMLWriter;
        $w->openMemory();
        $w->startDocument('1.0', 'UTF-8', 'yes');
        $w->startElement('worksheet');
        $w->writeAttribute('xmlns', 'http://schemas.openxmlformats.org/spreadsheetml/2006/main');
        $w->startElement('sheetData');
        foreach (array_values($rows) as $r => $row) {
            $number = $r + 1;
            $w->startElement('row');
            $w->writeAttribute('r', (string) $number);
            foreach (array_values($row) as $c => $value) {
                if ($value === null || $value === '') {
                    continue;
                }
                $ref = self::column($c).$number;
                $bold = $number <= 2;
                $num = self::numeric($value);
                $w->startElement('c');
                $w->writeAttribute('r', $ref);
                if ($num !== null) {
                    $w->writeAttribute('t', 'n');
                    $w->writeAttribute('s', $bold ? '1' : '0');
                    $w->writeElement('v', $num);
                } else {
                    $w->writeAttribute('t', 'inlineStr');
                    $w->writeAttribute('s', $bold ? '1' : '0');
                    $w->startElement('is');
                    $w->startElement('t');
                    $w->writeAttribute('xml:space', 'preserve');
                    $w->text(self::clean((string) $value));
                    $w->endElement();
                    $w->endElement();
                }
                $w->endElement();
            }
            $w->endElement();
        }
        $w->endElement();
        $w->endElement();

        return $w->outputMemory();
    }
}
