-- v2.19: nota de crédito electrónica (tipo 07) para anular comprobantes aceptados por SUNAT.
ALTER TABLE invoices ADD COLUMN ref_invoice_id BIGINT NULL, ADD COLUMN note_code CHAR(2) NULL, ADD COLUMN note_reason VARCHAR(250) NULL;
CREATE INDEX idx_invoices_ref ON invoices (ref_invoice_id);
-- Series de notas de crédito: BC01 para las que anulan boletas, FC01 para las que anulan facturas.
INSERT INTO invoice_series (doc_type, series, last_number, active)
SELECT '07', 'BC01', 0, TRUE FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM invoice_series WHERE series = 'BC01');
INSERT INTO invoice_series (doc_type, series, last_number, active)
SELECT '07', 'FC01', 0, TRUE FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM invoice_series WHERE series = 'FC01');
