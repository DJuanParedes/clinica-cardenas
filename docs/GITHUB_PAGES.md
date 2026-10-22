# Publicación de la interfaz

GitHub Pages sirve Angular. No ejecuta Java, MySQL, Eureka ni el gateway.

## Activación
En Settings > Pages, seleccionar GitHub Actions. El plan actual no admite Pages en este repositorio privado: requiere un plan compatible o una decisión explícita del propietario para hacerlo público. No cambiar la visibilidad sin esa decisión.

Al integrar un cambio en main, el flujo `Publicar interfaz en GitHub Pages` compila y despliega automáticamente. También admite Run workflow. Las propuestas de cambio solo compilan: no sustituyen la web publicada. El artefacto contiene únicamente la compilación de frontend, sin archivos Java, documentos académicos, bases de datos ni secretos.

Dirección prevista, pendiente de un despliegue exitoso: https://DJuanParedes.github.io/clinica-cardenas/

## Servidor pendiente
Sin PUBLIC_API_BASE_URL, muestra una portada de revisión y un aviso persistente. Bloquea solicitudes API y deshabilita ingreso y recuperación. No simula usuarios, pacientes ni OTP. Las rutas usan hash en Pages para permitir recargar enlaces. El modo local mantiene las rutas habituales y su proxy.

Cuando exista backend HTTPS, configurar la variable de Actions PUBLIC_API_BASE_URL con solo su origen (por ejemplo https://api.example.com), sin /api, credenciales ni secretos. Permitir en CORS del gateway el origen https://djuanparedes.github.io y los encabezados Authorization, Content-Type y X-Clinic-Id. Ejecutar nuevamente el flujo y verificar ingreso, recuperación y pacientes con datos de prueba autorizados. Una variable cambiada no despliega por sí sola. No colocar claves en variables públicas del frontend.

Actualizar un Sprint en el código y fusionarlo a main actualiza Pages. Cambiar solo la planificación fuera del repositorio no dispara el flujo. El backend necesita un despliegue separado.
