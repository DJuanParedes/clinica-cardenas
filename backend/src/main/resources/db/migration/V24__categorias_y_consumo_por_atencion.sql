-- v2.10: la cita y las reglas de comisión usan las mismas categorías que los procedimientos
-- (Diagnóstico, Prevención, Operatoria, Endodoncia, Cirugía, Prótesis, Implantología, Ortodoncia, Estética, Periodoncia).

ALTER TABLE appointments ADD COLUMN category VARCHAR(40) NULL AFTER reason;
ALTER TABLE commission_rules ADD COLUMN category VARCHAR(40) NULL AFTER procedure_id;

-- Citas existentes: se deduce la categoría del motivo (nombre de procedimiento o palabras clave).
UPDATE appointments a
   JOIN (SELECT a2.id, (SELECT p.category FROM procedures p
                         WHERE p.category IS NOT NULL AND a2.reason LIKE CONCAT('%', p.name, '%')
                         ORDER BY LENGTH(p.name) DESC LIMIT 1) AS cat
           FROM appointments a2) x ON x.id = a.id
   SET a.category = x.cat
 WHERE a.category IS NULL AND x.cat IS NOT NULL;
UPDATE appointments SET category = 'Ortodoncia'   WHERE category IS NULL AND reason LIKE '%ortodon%';
UPDATE appointments SET category = 'Endodoncia'   WHERE category IS NULL AND (reason LIKE '%endodon%' OR reason LIKE '%conducto%');
UPDATE appointments SET category = 'Prevención'   WHERE category IS NULL AND (reason LIKE '%profila%' OR reason LIKE '%limpieza%' OR reason LIKE '%flúor%');
UPDATE appointments SET category = 'Cirugía'      WHERE category IS NULL AND (reason LIKE '%exodon%' OR reason LIKE '%extrac%' OR reason LIKE '%cordal%');
UPDATE appointments SET category = 'Operatoria'   WHERE category IS NULL AND (reason LIKE '%restaura%' OR reason LIKE '%resina%' OR reason LIKE '%curaci%' OR reason LIKE '%caries%');
UPDATE appointments SET category = 'Estética'     WHERE category IS NULL AND reason LIKE '%blanquea%';
UPDATE appointments SET category = 'Implantología' WHERE category IS NULL AND reason LIKE '%implant%';
UPDATE appointments SET category = 'Prótesis'     WHERE category IS NULL AND (reason LIKE '%prótesis%' OR reason LIKE '%corona%');
UPDATE appointments SET category = 'Periodoncia'  WHERE category IS NULL AND (reason LIKE '%encía%' OR reason LIKE '%periodon%');
UPDATE appointments SET category = 'Diagnóstico'  WHERE category IS NULL AND (reason LIKE '%evaluaci%' OR reason LIKE '%control%' OR reason LIKE '%dolor%' OR reason LIKE '%consulta%' OR reason LIKE '%diagn%');

-- Reglas de un procedimiento puntual: guardan también su categoría (solo informativo).
UPDATE commission_rules r JOIN procedures p ON p.id = r.procedure_id SET r.category = p.category WHERE r.procedure_id IS NOT NULL;
