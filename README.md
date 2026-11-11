# Sistema Web CIMSE

Plataforma web de tres capas: **React** (frontend) + **Java Spring Boot** (API REST) + **PostgreSQL** (base de datos), todo con **Docker Compose**.

## Requisitos de la Opcion A (Docker)
- [Git](https://git-scm.com/)
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (abierto y corriendo)

Opcional, solo si quieres programar fuera de Docker: JDK 17, Maven 3.9+, Node 20 LTS.

## Opcion A: con Docker (recomendada)

```bash
git clone <URL-DEL-REPO>
cd cimse
docker compose up --build
```

La primera vez tarda varios minutos (descarga imágenes y dependencias). Cuando termine:

| Servicio | URL |
|---|---|
| Web (React) | http://localhost:5173 |
| API: salud y conexión a BD | http://localhost:8080/api/health |
| API: lista de tablas | http://localhost:8080/api/tablas |
| API: clientes (JPA) | http://localhost:8080/api/clientes |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| PostgreSQL | localhost:5433 · BD `cimse` · usuario `cimse_user` · clave `cimse_pass` |

En la web debe verse "🟢 Conectado a PostgreSQL" y la lista de tablas a la izquierda; al hacer clic en una, aparecen sus registros.

## Opcion B: sin Docker (PostgreSQL instalado en tu computadora)

Requisitos: PostgreSQL (12 o superior), **JDK 17 o 21** (no uses Java 25 ni 27) y **Node 20 LTS o superior**. **No necesitas instalar Maven**: el proyecto trae Maven Wrapper (`mvnw`) y descarga Maven solo la primera vez. Si tienes varios Java instalados, `iniciar_backend.bat` busca el 17 o el 21 por su cuenta.

1. **Base de datos:** doble clic en `scripts\instalar_bd.bat`. Te pide el puerto de tu PostgreSQL (Enter = 5433) y la contrasena del usuario `postgres`. Crea el usuario `cimse_user`, la base `cimse`, las 29 tablas y los datos de prueba. No necesitas pgAdmin.
2. **Backend:** doble clic en `scripts\iniciar_backend.bat` (pide el mismo puerto). Espera el mensaje `Started CimseApplication` y deja la ventana abierta.
3. **Frontend:** doble clic en `scripts\iniciar_frontend.bat` y abre http://localhost:5173.

Para apagar backend o frontend: Ctrl + C en su ventana.

### Mantener la base igual para todo el equipo
Las tablas viven en `database/schema.sql` y los datos de prueba en `database/seed.sql`, ambos en Git. Si alguien cambia la estructura de la base:
1. Modifica `schema.sql` (no solo su base local) y hace commit.
2. Los demas hacen `git pull` y vuelven a ejecutar `scripts\instalar_bd.bat` (borra y recrea la base `cimse`).

> No uses Docker y PostgreSQL local al mismo tiempo: los dos quieren el puerto 5433.

## Comandos útiles

```bash
docker compose up -d --build     # correr en segundo plano
docker compose logs -f backend   # ver logs del backend
docker compose down              # apagar
docker compose down -v           # apagar Y borrar la base de datos (se recrea desde database/*.sql)
```

> Los scripts de `database/` solo se ejecutan cuando la base se crea por primera vez.
> Si cambias `schema.sql` o `seed.sql`, corre `docker compose down -v` y luego `docker compose up --build`.

## Estructura

```
cimse/
├── backend/     Spring Boot (módulos: seguridad, personal, operaciones, comercial, administracion, proveedores, inventario, reportes, notificaciones)
├── frontend/    React + Vite
├── database/    schema.sql (tablas) y seed.sql (datos de prueba)
├── docs/        diagramas y evidencias
└── docker-compose.yml
```

## Flujo de trabajo en equipo (Git)

- `main`: versión estable. `develop`: integración. Cada quien trabaja en `feature/<nombre>-<tarea>`.
- Nunca se hace push directo a `main`: se abre un Pull Request y otro integrante lo revisa.

```bash
git checkout develop && git pull
git checkout -b feature/juan-clientes
# ...trabajar...
git add . && git commit -m "feat: pantalla de clientes"
git push -u origin feature/juan-clientes
# abrir Pull Request en GitHub hacia develop
```

## Problemas comunes

| Problema | Solución |
|---|---|
| `port is already allocated` (5433) | Otro programa usa el puerto 5433: ciérralo o cambia el número de la izquierda en `docker-compose.yml` (por ejemplo `"5434:5432"`) y en `application.yml` si corres el backend fuera de Docker. |
| La web dice "No hay conexión con el backend" | El backend aún está arrancando: espera ~30 s y recarga. Revisa `docker compose logs backend`. |
| Cambié el SQL y no se refleja | `docker compose down -v` y volver a levantar. |
| `docker: command not found` / error de daemon | Abre Docker Desktop y espera a que diga "running". |

## Evidencia de preparación técnica (por integrante)
Cada integrante: 1) clona el repo, 2) corre `docker compose up --build`, 3) captura la web mostrando la conexión y las tablas, 4) hace un commit en su rama y abre un Pull Request agregando su nombre en la tabla de abajo.

| Integrante | Cuenta | Corrió el proyecto | PR |
|---|---|---|---|
| Bayron Alexander Aguilera Funez | 20211001769 | ☐ | |
| Junior Manuel Quiroz Cortes | 20211031350 | ☐ | |
| Santiago José Posadas Hernández | 20201002816 | ☐ | |
| Christian Daniel Ponce Diaz | 20201002999 | ☐ | |
| Jonathan Fabricio Canales Duron | 20201000630 | ☐ | |

## Logo
Exporta el escudo del tigre desde Figma como PNG y guárdalo en `frontend/public/logo.png`; el menú lateral lo mostrará automáticamente.

## Base de datos
El modelo viene del script Oracle del equipo (`base_datos_seguridad.txt`) convertido a PostgreSQL: 29 tablas en `database/schema.sql` y datos de prueba en `database/seed.sql`.
Si ya tenías una versión anterior corriendo, recrea la base: `docker compose down -v` y luego `docker compose up --build`.

## Sprint 1 - Seguridad
Login con JWT, usuarios, roles dinámicos, recuperación de clave y auditoría: ver `docs/Sprint1_Seguridad.md`.
Las tablas nuevas (`roles`, `permisos`, `rol_permisos`, `solicitudes_clave`) están en `database/schema.sql` y `database/seed.sql`.
Si ya tenías la base creada: Docker -> `docker compose down -v` y luego `docker compose up --build`; local -> `scripts\instalar_bd.bat`.
Usuarios de prueba: `admin`, `supervisor1` y `gerente`, todos con la contraseña `123456`.

## Sprint 2 - Gestión Comercial y Relación Central
Clientes, contratos con alertas de vencimiento, puestos, turnos y asignación de guardias: ver `docs/Sprint2_Comercial.md`.
No cambia la base de datos. Colección de Postman de los Sprints 1 y 2: `docs/CIMSE.postman_collection.json`.

## Sprint 3 - Operaciones, Inventario y Activos Fijos
Supervisiones, incidencias, inventario con control único (serie y placa), asignación de equipos y actas digitales: ver `docs/Sprint3_Operaciones_Inventario.md`.
No cambia la base de datos.

## Ajustes de la revisión del 27 de octubre
Clave temporal por correo, varios roles por usuario, sueldos solo para el administrador, contactos de cliente, asignación de guardias por cliente y contrato en PDF: ver `docs/Ajustes_Revision_27oct.md`. Para el correo copie `.env.example` como `.env`.

## Revisión del 27 de octubre (completada)
Procedimiento de usuarios y bloqueo de cuenta: `docs/Procedimiento_Usuarios.md`. Lista de ajustes: `docs/Ajustes_Revision_27oct.md`.
Si ya tenía la base creada no hace falta recrearla: el backend repara los datos al iniciar. Reconstruya con `docker compose up --build`.

## Sprint 4
Proveedores, compras, pagos, permisos legales, reportes, notificaciones y despliegue en la nube (`docker-compose.prod.yml`, ver `docs/Despliegue_Nube.md`).
