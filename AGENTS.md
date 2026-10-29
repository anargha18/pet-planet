## Project documentation rules

The canonical technical documentation index is [`docs/project-context/README.md`](docs/project-context/README.md). `PROJECT_CONTEXT.md` is a compatibility notice that points to that index.

Before starting any task:
1. Read `docs/project-context/README.md` to understand the project and find the relevant technical documentation.
2. Read the relevant detailed files under `docs/project-context/` for the task (for example architecture, backend service, frontend, database, API, infrastructure, security, testing, or implementation status).
3. Inspect the relevant source files to verify that the documentation matches the actual code.
4. Identify the components affected by the requested change.

After implementing any change:
1. Update affected documentation when changes impact architecture, database schema, entities, APIs, frontend behavior, business logic, security, dependencies, environment configuration, infrastructure, testing, or implementation status.
2. Document new database tables, columns, relationships, constraints, and migrations accurately.
3. Update API documentation when endpoints, request/response structures, validation rules, or authorization rules change.
4. Update cross-references, the documentation index, architecture diagrams, and workflows when relevant.
5. Remove or revise only documentation that has become outdated; avoid rewriting unrelated documentation.
6. Never document planned or partially implemented functionality as complete.
7. Do not change verified facts without source evidence.
8. If the implementation has not changed any documented behavior, do not make unnecessary edits.

Before completing the task:
- Review code and documentation changes together; ensure the documentation reflects the final implementation.
- Check relative links and keep the index accurate when documentation files are added, renamed, or removed.
- Report documentation changes and verification gaps at the end of the task.
- Explicitly mention any details that could not be verified.

The source code and database migrations remain the primary evidence of implementation. Avoid conflicting duplicate AI instructions; update this file rather than creating a second project-specific instruction source.