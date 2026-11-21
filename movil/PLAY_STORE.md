# Publicar la app en Google Play

La app ya está lista para subirse: identificador `pe.clinicacardenas.pacientes`, versión 2.0.0 (código 2),
firma configurable y compilación automática en GitHub Actions. Lo que falta solo lo puede hacer la clínica
(cuenta, pago y datos legales).

## 1. Cuenta de desarrollador (la clínica)
1. Crear la cuenta en https://play.google.com/console (pago único de 25 USD).
2. Elegir cuenta de **organización** si la clínica tiene RUC; Google pide verificar identidad y un teléfono.

## 2. Crear la llave de firma (una sola vez, guardarla para siempre)
En una PC con Java instalado:

```
keytool -genkeypair -v -keystore clinica.jks -alias clinica -keyalg RSA -keysize 2048 -validity 10000
```

- Guarda `clinica.jks` y las contraseñas en un lugar seguro (si se pierde, no se puede actualizar la app).
- **No** se sube al repositorio (`*.jks` está en `.gitignore`).

## 3. Compilar el paquete para Play (AAB)
**Opción A — GitHub Actions (recomendada).** En el repositorio: *Settings → Secrets and variables → Actions* y crear:

| Secreto | Valor |
|---|---|
| `ANDROID_KEYSTORE_B64` | el archivo `clinica.jks` en base64 (`base64 -w0 clinica.jks`) |
| `ANDROID_KEYSTORE_PASSWORD` | contraseña del almacén |
| `ANDROID_KEY_ALIAS` | `clinica` |
| `ANDROID_KEY_PASSWORD` | contraseña de la llave |

En cada push, la pestaña **Actions** deja el `.aab` firmado como artefacto descargable.

**Opción B — en la PC:** definir las variables `CLINICA_KEYSTORE` (ruta al .jks), `CLINICA_KEYSTORE_PASSWORD`,
`CLINICA_KEY_ALIAS` y `CLINICA_KEY_PASSWORD`, y ejecutar `gradle :composeApp:bundleRelease` dentro de `movil`.

## 4. Ficha de la tienda
- Nombre: *Clínica Dental Cárdenas*. Descripción corta: “Tus citas, pagos y documentos de la clínica”.
- Ícono 512×512, imagen destacada 1024×500 y al menos 2 capturas del teléfono.
- **Política de privacidad** (obligatoria por manejar datos de salud): publicar una página con lo que se recoge
  (DNI, teléfono, citas, historia clínica), para qué (atención, recordatorios, facturación), dónde se guarda,
  cuánto tiempo y cómo pedir su eliminación — en línea con la Ley N.° 29733 y su reglamento.
- Formulario **Seguridad de los datos**: declarar datos personales, de salud y financieros; cifrado en tránsito (HTTPS).
- Clasificación de contenido: cuestionario (resultado esperado: “Para todos”).
- Público objetivo: mayores de 18 (los menores usan la cuenta de su apoderado).

## 5. Antes de enviar
- La app debe apuntar al servidor en internet (HTTPS). Cambiar `defaultServerUrl` en
  `AndroidPlatformActions.kt` por `https://<dominio-de-la-clínica>` y subir `versionCode`.
- Quitar `usesCleartextTraffic`/excepciones de `network_security_config.xml` que solo sirven para la red local.
- Probar en la pista **Prueba interna** con 2–3 teléfonos, luego **Producción**.
- Los pagos en línea están en modo simulado: para cobrar de verdad hay que contratar una pasarela (Niubiz,
  Izipay, Culqi o Mercado Pago) y cambiar `PAGOS_MODO` (ver `docs/PAGOS_Y_SUNAT_REAL.md`).
