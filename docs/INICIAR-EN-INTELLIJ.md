# Iniciar en IntelliJ IDEA

## Requisitos

- Java 21 configurado como Project SDK.
- Maven integrado de IntelliJ o Maven 3.9+.
- Node.js 20.19+, 22.12+ o 24+.

## Importación

1. Selecciona **Open** y abre la carpeta raíz.
2. Cuando IntelliJ detecte `pom.xml`, selecciona **Load Maven Project**.
3. Espera a que se importen todos los módulos.

## Orden de ejecución

1. `EurekaServerApplication` — puerto 8761.
2. Los servicios de dominio, en cualquier orden:
   - `AuthServiceApplication` — 8081.
   - `PatientServiceApplication` — 8082.
   - `AppointmentServiceApplication` — 8083.
   - `ClinicalServiceApplication` — 8084.
   - `FinanceServiceApplication` — 8085.
   - `LabServiceApplication` — 8086.
   - `OperationsServiceApplication` — 8087.
   - `ContentServiceApplication` — 8089.
3. `DashboardServiceApplication` — 8088.
4. `ApiGatewayApplication` — 8090.
5. En la terminal:

```powershell
cd frontend
npm install
npm start
```

Abre `http://localhost:4200`.

## Verificación

- Eureka: `http://localhost:8761`.
- Gateway: `http://localhost:8090/actuator/health`.
- Ejecuta `PROBAR-SISTEMA.ps1` desde PowerShell para verificar login y rutas.

El frontend nunca debe apuntar directamente a `8081`, `8082` u otro microservicio. Siempre usa el Gateway `8090`.
