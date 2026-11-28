-- v2.18: el tratamiento empieza cuando el paciente acepta el presupuesto.
-- Los planes que ya existían se dan por aceptados para no frenar tratamientos en curso.
ALTER TABLE treatment_plans ADD COLUMN accepted_at DATETIME NULL, ADD COLUMN accepted_by VARCHAR(40) NULL, ADD COLUMN accepted_via VARCHAR(20) NULL;
UPDATE treatment_plans SET accepted_at = created_at, accepted_by = created_by, accepted_via = 'PREVIO';
