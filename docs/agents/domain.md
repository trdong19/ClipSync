# Domain Documentation

## Layout

Single-context: one `CONTEXT.md` at the repo root, ADRs in `docs/adr/`.

## Consumer Rules

Skills that read domain docs:

- `improve-codebase-architecture` — reads `CONTEXT.md` for domain language, `docs/adr/` for past decisions
- `diagnose` — reads `CONTEXT.md` for mental model
- `tdd` — reads `CONTEXT.md` for domain terms used in test names

## What Goes Where

- **`CONTEXT.md`** — project domain language, core concepts, key terms. Written and maintained by humans.
- **`docs/adr/*.md`** — Architecture Decision Records. One per significant decision. Format: Title, Status, Context, Decision, Consequences.
