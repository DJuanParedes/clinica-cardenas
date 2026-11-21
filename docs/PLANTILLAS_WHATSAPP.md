# Plantillas de WhatsApp (Meta)

Fuera de las 24 horas desde el último mensaje del paciente, WhatsApp solo entrega **plantillas aprobadas por Meta**.
El sistema elige la plantilla de cada aviso; si Meta todavía no la aprobó, usa la plantilla general `aviso_clinica`
con el texto en una sola línea (y Meta puede rechazarla si es publicidad).

Se crean en **WhatsApp Manager → Plantillas de mensajes → Crear plantilla**, idioma **Spanish (es)**.
El nombre debe ser exactamente el de la tabla. Los ejemplos son los valores que pide Meta para revisar.

## Avisos de citas (desde v2.3)
`recordatorio_cita`, `cita_registrada`, `cita_reprogramada`, `cita_cancelada` y la general `aviso_clinica` (Utilidad).

## Nuevas en v2.9

### 1. `novedad_clinica` · Marketing · campañas
```
Hola, te escribe *{{1}}* con una novedad para ti:

{{2}}

Puedes responder a este mensaje para separar tu cita. Si ya no deseas recibir promociones, responde BAJA.
```
Ejemplos: {{1}} `Clínica Dental Cárdenas` · {{2}} `Hola Sofía, este mes la limpieza dental tiene 20 % de descuento.`
Botón opcional: respuesta rápida «BAJA» (el sistema ya entiende la palabra BAJA escrita).

### 2. `cumpleanos_clinica` · Marketing · saludo de cumpleaños
```
¡Feliz cumpleaños! 🎂 Te saluda todo el equipo de *{{1}}*:

{{2}}

Si ya no deseas recibir estos mensajes, responde BAJA.
```
Ejemplos: {{1}} `Clínica Dental Cárdenas` · {{2}} `Hola Sofía, te deseamos un año lleno de sonrisas. Este mes tienes una limpieza de regalo.`

### 3. `documento_por_firmar` · Utilidad · consentimientos a distancia
```
Hola {{1}}, *{{2}}* te envía el documento *{{3}}* para que lo revises y firmes desde tu celular:

{{4}}

El enlace vence en {{5}} días. Si tienes dudas, escríbenos por aquí antes de firmar.
```
Ejemplos: {{1}} `Sofía` · {{2}} `Clínica Dental Cárdenas` · {{3}} `consentimiento informado de endodoncia` ·
{{4}} `https://clinica-cardenas.duckdns.org/firmar/abc123` · {{5}} `7`

## Notas
- Las plantillas de **Marketing** solo se envían a pacientes con permiso de publicidad (campañas) o que no lo
  rechazaron (cumpleaños). Meta cobra por cada conversación de marketing; las de utilidad dentro de la ventana de
  atención son gratuitas o más baratas.
- Si Meta cambia la categoría (por ejemplo, de Utilidad a Marketing) la plantilla sigue funcionando.
- El estado de la plantilla principal se ve en **Notificaciones → Panel de WhatsApp**.
