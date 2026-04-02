# AppTick Architecture Reference

> Comprehensive documentation for Claude Code and human developers.
> Read CLAUDE.md first — it contains critical invariants that must never be broken.

This document is split into topic files under `docs/architecture/`. Read the relevant file(s) when you need context on a specific area.

| # | Topic | File |
|---|-------|------|
| 1-2 | Project overview & package structure | [01-overview-and-structure.md](docs/architecture/01-overview-and-structure.md) |
| 3 | Core blocking pipeline & timing constants | [02-blocking-pipeline.md](docs/architecture/02-blocking-pipeline.md) |
| 4 | Background services, battery, notifications | [03-background-services.md](docs/architecture/03-background-services.md) |
| 5 | Accessibility service & floating windows | [04-accessibility-floating-windows.md](docs/architecture/04-accessibility-floating-windows.md) |
| 6 | Data layer (Room, DAO, migrations, backup) | [05-data-layer.md](docs/architecture/05-data-layer.md) |
| 7 | Domain models & group UI (drag-and-drop) | [06-domain-models-groups.md](docs/architecture/06-domain-models-groups.md) |
| 8 | App limit evaluation & time logic | [07-app-limit-evaluation.md](docs/architecture/07-app-limit-evaluation.md) |
| 9 | UI layer (navigation, create/edit, block screen) | [08-ui-layer.md](docs/architecture/08-ui-layer.md) |
| 10 | Lock modes & premium features | [09-lock-modes-premium.md](docs/architecture/09-lock-modes-premium.md) |
| 11-12 | Settings, theme, device apps, utilities | [10-settings-theme-utilities.md](docs/architecture/10-settings-theme-utilities.md) |
| 13 | Test suite (unit + integration) | [11-test-suite.md](docs/architecture/11-test-suite.md) |
| 14-15 | SharedPreferences & intent/broadcast reference | [12-references.md](docs/architecture/12-references.md) |
