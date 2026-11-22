# Pet Planet

An event-driven pet shop built to learn microservices: ten Spring Boot services, Apache Kafka, one PostgreSQL database per service, and a React storefront.

```sh
make up      # build and start everything, then open http://localhost:5173
make seed    # optional demo data
```

Sign in as `customer@petplanet.com` or `admin@petplanet.com` (password `password123`).

**All documentation is in [`docs/`](docs/README.md)**, covering architecture, the event-driven saga, services, the API, the database, frontend, security, code structure and design patterns, infrastructure, testing and project status.
