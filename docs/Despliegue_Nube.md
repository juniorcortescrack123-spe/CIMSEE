# Manual de despliegue (Sprint 4 - Docker Compose en la nube)

## Requisitos del servidor
Cualquier servidor Linux (VPS) con Docker y Docker Compose, 2 GB de RAM y el puerto 80 abierto. La primera carga de la base y la compilación tardan unos minutos.

## Pasos
1. Subir el proyecto al servidor (`git clone` del repositorio o copiar la carpeta `cimse`).
2. `cp .env.example .env` y editar: `DB_PASS` (contraseña de la base), `APP_URL` (dirección pública, por ejemplo `http://IP-DEL-SERVIDOR`) y los datos del correo (`MAIL_*`) si se quiere enviar claves por correo.
3. Levantar: `docker compose -f docker-compose.prod.yml up -d --build`
4. Abrir `http://IP-DEL-SERVIDOR` y entrar con el usuario `admin`. **Cambie la contraseña de los usuarios de prueba** (Seguridad > Usuarios > Restaurar contraseña) o elimine los que no use.

## Cómo funciona
- `frontend` (nginx, puerto 80) sirve React y reenvía `/api` al `backend` (Spring Boot). El navegador solo ve un puerto.
- `backend` y `db` (PostgreSQL) quedan en la red interna: la base no se publica al exterior.
- Volúmenes: `pgdata` (base de datos) y `uploads` (contratos escaneados en PDF). Se conservan aunque se reinicien los contenedores.

## Operación
- Ver estado: `docker compose -f docker-compose.prod.yml ps` · Logs: `docker compose -f docker-compose.prod.yml logs -f backend`
- Actualizar versión: `git pull` y volver a ejecutar el paso 3.
- Respaldo de la base: `docker exec cimse-db pg_dump -U cimse_user cimse > respaldo.sql`
- Empezar de cero (BORRA TODO): `docker compose -f docker-compose.prod.yml down -v`

## Prueba rápida (smoke test) antes de la defensa
`docker compose up --build` en local (usa el `docker-compose.yml` normal, puertos 5173 y 8080) y luego el mismo despliegue de producción en el servidor.
