# PostgreSQL initialization

`01-create-catalog-database.sh` runs once, when the Postgres container starts with an **empty** data volume. It creates one database and one owner role per service (catalog, order, inventory, payment, notification, shipping, delivery, fulfillment, user) from the `*_DATABASE_NAME`, `*_DATABASE_USERNAME` and `*_DATABASE_PASSWORD` variables passed by `docker-compose.yml`. Each service then creates its own schema with Flyway on startup.

Because the script only runs on an empty volume, changes to it (or a missing database) require recreating the volume: `make reset`, which deletes all local data.

Keep real credentials in the root `.env` file; do not commit secrets.
