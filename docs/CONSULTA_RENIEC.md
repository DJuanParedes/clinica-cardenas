# Autocompletar pacientes con RENIEC

Al escribir un DNI de 8 dígitos en **Registrar paciente** (y en el DNI del representante legal de un menor),
el sistema consulta RENIEC y llena los campos vacíos. Lo que el personal ya escribió no se pisa, y si se
cambia el número se borra lo que se autocompletó para el número anterior. Siempre se revisa antes de guardar.

## Qué datos llegan

RENIEC no tiene una API pública. La consulta se hace a través de un **proveedor autorizado** que recibe el DNI
y un token. Lo que devuelve depende del plan del proveedor:

| Dato | Planes gratuitos (p. ej. Decolecta) | Planes con ficha ampliada |
|---|---|---|
| Nombres y apellidos | ✔ | ✔ |
| Fecha de nacimiento, sexo | — | ✔ |
| Dirección, UBIGEO | — | ✔ |

La fecha de nacimiento, el sexo y la dirección son datos personales protegidos (Ley 29733); RENIEC no los entrega
en su consulta gratuita. Si el proveedor no los envía, el personal los toma del DNI físico del paciente.

## Configuración en el servidor

En el archivo `.env` del servidor:

```
RENIEC_API_TOKEN=<token del proveedor>
# opcional: otro proveedor; {dni} se reemplaza por el número
RENIEC_API_URL=https://api.decolecta.com/v1/reniec/dni?numero={dni}
```

Luego `docker compose up -d backend`. Sin token, el formulario lo indica y los datos se escriben a mano.

El sistema entiende las respuestas de Decolecta (antes apis.net.pe), Factiliza, apiperu.dev y similares
(campos `first_name`/`nombres`, `first_last_name`/`apellido_paterno`, `fecha_nacimiento`, `sexo`, `direccion`,
`ubigeo_reniec`…). Las consultas se guardan en caché 24 h para no gastar el cupo del plan.

Errores que ve el personal: DNI no registrado en RENIEC, token rechazado o vencido, límite de consultas del plan
alcanzado, o proveedor sin respuesta. En todos los casos se puede seguir escribiendo a mano.
