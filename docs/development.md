# Development

This page describes how to set up a development environment and work on
Luogo-FOSS.

## Requirements

- Android Studio (latest stable)
- JDK 17
- (Backend) Docker, if self-hosting

## Getting the code

```bash
git clone https://github.com/anshlabs716/Luogo-FOSS.git
cd Luogo-FOSS
```

## Build

```bash
./gradlew assembleDebug
```

## Run

```bash
./gradlew installDebug
```

## Test

```bash
./gradlew test
```

## Workflow

- Branch from `main` (see [CONTRIBUTING.md](../CONTRIBUTING.md)).
- Keep changes focused and documented.
- Update `CHANGELOG.md` for user-facing changes.
