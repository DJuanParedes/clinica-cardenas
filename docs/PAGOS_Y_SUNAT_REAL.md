# Pasar a producción: SUNAT real, pagos reales y notificaciones push

Hoy el sistema funciona completo con servicios **simulados** (decisión del proyecto: “simuladas ahora,
reales después”). Este documento indica qué contratar y qué cambiar para cada uno.

## 1. Facturación electrónica SUNAT (real)
El backend ya genera el XML UBL 2.1, lo firma y lo envía; hoy apunta al simulador `sunat-sim`.

**Requisitos de la clínica (RUC 10102984841):**
1. Afiliarse como emisor electrónico en SOL (SUNAT Operaciones en Línea) — sistema “Del contribuyente” (SEE-Del contribuyente).
2. Crear un **usuario secundario SOL** con perfil de emisión electrónica (usuario y clave para el web service).
3. Comprar un **certificado digital** de una entidad acreditada por INDECOPI (ej. Llama.pe, Camerfirma,
   Certicámara) en formato `.pfx`/`.p12`. Costo aproximado: S/ 150–300 al año.
4. Pasar el proceso de **homologación** en el entorno beta de SUNAT (enviar los casos de prueba de boletas,
   facturas y notas de crédito). Alternativa más rápida: usar un **OSE** (Operador de Servicios Electrónicos).

**Cambios en el sistema** (variables del servicio `backend`; en `docker-compose.yml` hoy `SUNAT_URL`
apunta al simulador, así que se reemplaza esa línea y se agregan las demás):
```
SUNAT_AMBIENTE=PRODUCCION
SUNAT_URL=https://e-factura.sunat.gob.pe/ol-ti-itcpfegem/billService   # producción
SUNAT_USUARIO=10102984841USUARIO          # RUC + usuario secundario SOL
SUNAT_CLAVE=********
SUNAT_CERTIFICADO=file:/data/certificado.p12   # montar el archivo como volumen de solo lectura
SUNAT_CERTIFICADO_CLAVE=********
```
- Beta (pruebas): `https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService`.
- Las boletas se envían en **resumen diario**; las facturas, una por una. El sistema ya distingue ambos casos.
- Conservar los XML y CDR al menos 5 años (ya se guardan en la base de datos; incluir en el respaldo diario).

## 2. Pagos en línea (Yape, Plin, tarjeta)
La app y el backend usan `SimulatedPaymentGateway` (`PAGOS_MODO=simulado`). Para cobrar de verdad:

| Pasarela | Yape | Tarjetas | Comisión aprox. |
|---|---|---|---|
| Niubiz (VisaNet) | Sí (QR) | Visa, MC, Amex | 3.5–4 % |
| Izipay | Sí | Todas | 3.4–3.9 % |
| Culqi | Sí (Yape API) | Todas | 3.44 % + IGV |
| Mercado Pago | Sí | Todas | 3.99 % + IGV |

Pasos: contratar la pasarela (piden RUC, cuenta bancaria y web con términos y condiciones), obtener sus
llaves de prueba y producción, e implementar `PaymentGateway` con su SDK/API (el resto —registro del pago,
boleta automática y aviso en la app— ya está hecho). **Nunca** guardar números de tarjeta en la base de datos:
con una pasarela real la app usa el formulario/tokenización de la pasarela (cumplimiento PCI-DSS).

## 3. Notificaciones push reales
Hoy la app revisa avisos cada minuto mientras está abierta y los muestra como notificación del teléfono.
Para avisos con la app cerrada:
1. Crear un proyecto en **Firebase** y agregar la app Android (`pe.clinicacardenas.pacientes`); descargar
   `google-services.json` a `movil/composeApp/`.
2. Agregar Firebase Cloud Messaging a la app y registrar el token del teléfono en el backend
   (nuevo campo `push_token` del paciente).
3. En `NoticeService.add(...)`, además de guardar el aviso, enviarlo por FCM con la cuenta de servicio.
En iOS se usa APNs a través del mismo Firebase.

## 4. WhatsApp con el número propio de la clínica
Ver `PENDIENTES.md`. El webhook ya está listo en `https://<dominio>/webhook/whatsapp`: en Meta → WhatsApp →
Configuración → Webhook, pegar esa URL y el `WA_VERIFY_TOKEN` del `.env` del servidor, y suscribir los
campos **messages** (incluye estados entregado/leído y respuestas). Opcional: `WA_APP_SECRET` para validar firmas.

## 5. Verificación en 2 pasos y datos personales
- Cada usuario del personal la activa en la web: **Mi seguridad**. Recomendado obligatorio para administrador y dirección.
- Inscribir el banco de datos “Pacientes” ante la Autoridad Nacional de Protección de Datos Personales (MINJUSDH);
  el sistema ya registra el consentimiento de cada paciente (fecha y medio: app o recepción).
