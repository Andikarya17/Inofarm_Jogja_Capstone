-- Read-only: run in phpMyAdmin SQL tab or Workbench on inofarm_local.
-- DEMO ONLY: all thresholds and source timestamps are synthetic.
SELECT e.id, e.kind,
       JSON_UNQUOTE(JSON_EXTRACT(e.body, '$.confirmedAt')) AS waktu_sumber_raw,
       CONVERT_TZ(e.source_at, '+00:00', '+07:00') AS waktu_sumber_dipercaya_wib,
       e.clock_trusted,
       CONVERT_TZ(e.received_at, '+00:00', '+07:00') AS diterima_backend_wib,
       e.phase, e.manual_ppm,
       JSON_UNQUOTE(JSON_EXTRACT(e.validation_result, '$.status')) AS status_utama,
       JSON_UNQUOTE(JSON_EXTRACT(e.assignment, '$.workerNameSnapshot')) AS pekerja_terjadwal,
       JSON_UNQUOTE(JSON_EXTRACT(e.assignment, '$.status')) AS status_assignment,
       JSON_UNQUOTE(JSON_EXTRACT(e.body, '$.notes')) AS skenario_dummy,
       JSON_EXTRACT(e.body, '$.sensorSnapshot') AS snapshot_sensor,
       JSON_EXTRACT(e.validation_result, '$.checks') AS seluruh_pemeriksaan,
       e.config_version_id, e.phase_version_id, e.device_id, e.tank_id
FROM events AS e
JOIN tanks AS t ON t.id = e.tank_id
WHERE t.name = 'DEMO DUMMY - BUKAN DATA GREENHOUSE' AND e.kind = 'record'
ORDER BY e.id DESC
LIMIT 100;
