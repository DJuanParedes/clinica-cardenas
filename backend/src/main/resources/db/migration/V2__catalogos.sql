-- Catálogos base del Sprint 1

INSERT INTO roles (code, name, description) VALUES
 ('ADMIN',     'Administrador',        'Gestiona usuarios, roles y configuración general'),
 ('RECEPCION', 'Recepcionista',        'Registra pacientes y programa citas'),
 ('ODONTOLOGO','Odontólogo',           'Atiende pacientes, historia clínica, odontograma y plan de tratamiento'),
 ('CAJA',      'Responsable de caja',  'Emite comprobantes y los envía a SUNAT'),
 ('DIRECTOR',  'Director de la clínica','Consulta indicadores y agenda');

INSERT INTO procedures (code, name, category, price) VALUES
 ('CON-001', 'Consulta y evaluación odontológica',  'Diagnóstico',   50.00),
 ('RAD-001', 'Radiografía periapical',              'Diagnóstico',   35.00),
 ('RAD-002', 'Radiografía panorámica',              'Diagnóstico',   80.00),
 ('PRE-001', 'Profilaxis y destartraje',            'Prevención',   120.00),
 ('PRE-002', 'Aplicación de flúor',                 'Prevención',    60.00),
 ('PRE-003', 'Sellante de fosas y fisuras (pieza)', 'Prevención',    70.00),
 ('OPE-001', 'Restauración con resina simple',      'Operatoria',   130.00),
 ('OPE-002', 'Restauración con resina compuesta',   'Operatoria',   180.00),
 ('END-001', 'Endodoncia unirradicular',            'Endodoncia',   450.00),
 ('END-002', 'Endodoncia multirradicular',          'Endodoncia',   650.00),
 ('CIR-001', 'Exodoncia simple',                    'Cirugía',      120.00),
 ('CIR-002', 'Exodoncia de tercera molar',          'Cirugía',      380.00),
 ('PRO-001', 'Corona de porcelana',                 'Prótesis',     950.00),
 ('PRO-002', 'Corona metal-cerámica',               'Prótesis',     700.00),
 ('IMP-001', 'Implante dental (fase quirúrgica)',   'Implantología',2800.00),
 ('ORT-001', 'Control de ortodoncia',               'Ortodoncia',   150.00),
 ('EST-001', 'Blanqueamiento dental',               'Estética',     600.00),
 ('PER-001', 'Curetaje periodontal por cuadrante',  'Periodoncia',  220.00);

INSERT INTO invoice_series (doc_type, series, last_number) VALUES
 ('03', 'B001', 0),
 ('01', 'F001', 0);
