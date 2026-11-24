# Inscripción de los bancos de datos ante la ANPD (Ley 29733)

Es un trámite de la clínica (titular de los datos), no del sistema. Desde el 31 de marzo de 2025, con el
reglamento aprobado por DS 016-2024-JUS, la inscripción es **gratuita, virtual y de aprobación automática**
(sujeta a fiscalización posterior).

## Dónde
Sistema Integrado de Protección de Datos Personales (SIPDP): https://sipdp.minjus.gob.pe/sipdp-virtual/public/login.xhtml
Ficha del trámite: https://www.gob.pe/8060-inscribir-banco-de-datos-en-el-registro-nacional-de-proteccion-de-datos-personales
Consultas: registropdp@minjus.gob.pe

## Qué se necesita
1. Crear usuario en el SIPDP como titular (persona natural con negocio, RUC 10102984841).
2. Llenar un **formulario de inscripción por cada banco** y adjuntarlo firmado.
3. Al terminar se obtiene la constancia y el **código del banco de datos** (guardarlo: se menciona en la política de privacidad).

## Bancos de datos que maneja el sistema
| Banco | Datos | Finalidad | Datos sensibles |
|---|---|---|---|
| **Pacientes e historias clínicas** | Identificación, contacto, fecha de nacimiento, apoderado de menores, historia clínica, odontograma, anamnesis, radiografías y fotos, consentimientos firmados | Atención odontológica, citas, facturación | **Sí (salud)** |
| **Prospectos y publicidad** (leads, campañas, cumpleaños) | Nombre, celular, correo, permiso de publicidad | Marketing con consentimiento | No |
| **Libro de reclamaciones** | Identificación, domicilio, contacto, detalle del reclamo | Atender reclamos (obligación legal) | No (salvo lo que el paciente escriba) |
| **Personal** (usuarios del sistema) | Nombre, documento, usuario, registro de accesos | Control de acceso y auditoría | No |

## Datos que suele pedir el formulario (tenerlos a la mano)
- **Ubicación del banco:** servidor en Microsoft Azure (región de la VM) → hay **flujo transfronterizo**
  (los datos se alojan fuera del Perú). Los mensajes pasan por Meta (WhatsApp) y el correo por el proveedor SMTP.
- **Encargados de tratamiento:** Microsoft (alojamiento), Meta (WhatsApp Business), proveedor de correo,
  Mercado Pago si se activa el pago en línea.
- **Medidas de seguridad:** acceso con usuario y contraseña, verificación en dos pasos opcional, roles y permisos,
  registro de auditoría, HTTPS, copia de seguridad diaria cifrada en tránsito con copia fuera del servidor.
- **Plazo de conservación:** historia clínica según la norma técnica de historia clínica del MINSA (NTS 139-MINSA; confirmar el plazo vigente);
  publicidad hasta que el paciente se dé de baja; reclamos 2 años como mínimo.

## Después de inscribir
- Poner el código del banco en `frontend/public/privacidad.html` (política de privacidad).
- Mantener la inscripción actualizada si cambia el servidor, los encargados o las finalidades (trámite de modificación).
