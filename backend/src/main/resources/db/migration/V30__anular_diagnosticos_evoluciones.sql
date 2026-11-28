-- v2.16: la historia clínica no se borra (NTS 139-MINSA). Un diagnóstico o una evolución
-- registrados por error se anulan con motivo: siguen visibles, tachados, con quién y cuándo.
ALTER TABLE diagnoses  ADD COLUMN voided_at DATETIME NULL, ADD COLUMN voided_by VARCHAR(40) NULL, ADD COLUMN void_reason VARCHAR(300) NULL;
ALTER TABLE evolutions ADD COLUMN voided_at DATETIME NULL, ADD COLUMN voided_by VARCHAR(40) NULL, ADD COLUMN void_reason VARCHAR(300) NULL;
