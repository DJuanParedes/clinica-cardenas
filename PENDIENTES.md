# Pendientes

## WhatsApp con número propio de la clínica
Hoy los mensajes salen del **número de prueba de Meta** (+1 555 171 1339), que solo llega a 5 números verificados a mano.
El sistema ya está listo para escribir a todos los pacientes (`DESTINOS_REALES=pacientes`, agenda automática, token permanente).

Para activarlo:
1. Conseguir un celular para la clínica que **no esté en WhatsApp** (recibe el código por SMS o llamada).
2. En WhatsApp Manager → **Números de teléfono → Agregar**, registrarlo y confirmar el código.
3. Agregar un **método de pago** en la cuenta de WhatsApp (Meta cobra cada mensaje enviado con plantilla).
4. Esperar la aprobación del **nombre visible** "Clínica Dental Cárdenas".
5. Copiar el nuevo *Phone Number ID* en `WA_PHONE_NUMBER_ID` del `.env` y ejecutar `actualizar.bat`.

## No ejecutar las pruebas automáticas en producción
Las pruebas de `pruebas/` crean pacientes con números inventados. En producción podrían recibir mensajes reales.

## Servicios que hoy están simulados (pasar a reales cuando la clínica los contrate)
- **Facturación SUNAT real:** certificado digital, usuario secundario SOL y homologación (u OSE). Ver `docs/PAGOS_Y_SUNAT_REAL.md`.
- **Pagos en línea reales:** contratar Niubiz, Izipay, Culqi o Mercado Pago (hoy `PAGOS_MODO=simulado`).
- **Notificaciones push con la app cerrada:** Firebase Cloud Messaging (hoy la app revisa avisos cada minuto mientras está abierta).
- **Google Play:** cuenta de desarrollador (25 USD), llave de firma y política de privacidad. Ver `movil/PLAY_STORE.md`.
- **Webhook de WhatsApp:** cuando el servidor en la nube esté arriba, registrar `https://<dominio>/webhook/whatsapp` en Meta con el `WA_VERIFY_TOKEN`.
