# Flujo de ramas y versiones

Las ramas siguen una jerarquía: cada rama sabe de cuál nace y a cuál vuelve, y el nombre lo dice.

```
main                         ← entregas cerradas (fin de sprint / evaluación)
 └─ sprint-final-todas-las-hu ← rama de despliegue: el servidor se actualiza solo desde aquí
     └─ release/2.3.0         ← agrupa todo lo de una versión (web + API + app)
         ├─ feat/2.3.0/api        ← backend y base de datos
         ├─ feat/2.3.0/web        ← sistema web (personal y portal del paciente)
         └─ feat/2.3.0/movil      ← app del paciente, pruebas y documentación
```

## Nombres

| Tipo | Patrón | Nace de | Vuelve a |
|---|---|---|---|
| Versión | `release/<versión>` | rama de despliegue | rama de despliegue (PR) |
| Funcionalidad | `feat/<versión>/<área>` | `release/<versión>` | `release/<versión>` (PR) |
| Corrección | `fix/<versión>/<tema>` | `release/<versión>` o rama de despliegue | la rama de la que nació |

Áreas: `api`, `web`, `movil`, `deploy`, `pruebas`. La versión es la misma que muestra la app (`versionName` en `movil/composeApp/build.gradle.kts`).

## Commits

Formato *Conventional Commits* en español:

```
feat(portal): el paciente ve sus tratamientos y pagos
fix(inventario): el modal de compra no se abre vacío
test(portal): pruebas de archivos solicitados
docs: flujo de ramas
```

Tipos: `feat`, `fix`, `test`, `docs`, `refactor`, `chore`. El ámbito entre paréntesis es el módulo (`portal`, `caja`, `agenda`, `login`, `movil`, `db`…).

## Pull requests

1. Cada `feat/...` se integra a su `release/...` con un PR que explica qué cambia y cómo se probó.
2. Cuando la versión pasa las pruebas (`pruebas/*-e2e.js` contra el entorno local, nunca contra producción), se abre el PR `release/<versión>` → `sprint-final-todas-las-hu`.
3. Al unirse, el servidor se actualiza solo en unos minutos (`deploy/actualizar.sh` respalda la base antes) y Flyway aplica las migraciones nuevas.
4. Al cerrar el sprint, `sprint-final-todas-las-hu` → `main`.

Las ramas `feat/...` se borran después de unirse; las `release/...` se conservan como historial de versiones.
