# Arquitectura

```text
Angular / PWA
     |
     v
API Gateway :8090
     |
     +--> Auth :8081 -------- Base AUTH
     +--> Pacientes :8082 --- Base PACIENTES
     +--> Citas :8083 ------- Base CITAS
     +--> Clínica :8084 ----- Base CLINICA
     +--> Finanzas :8085 ---- Base FINANZAS
     +--> Laboratorio :8086 - Base LABORATORIO
     +--> Operaciones :8087 - Base OPERACIONES
     +--> Dashboard :8088 --- Agregación por API
     +--> Contenido :8089 --- Portal público

Todos los servicios se registran en Eureka :8761.
```

No existe acceso directo desde Angular a las bases de datos ni a los microservicios. El Gateway es la única entrada pública del backend.
