# Testing

This page describes how Luogo-FOSS is tested and how to run the tests yourself.

## Running the tests

```bash
./gradlew test
```

## What is covered

Unit tests plus manual security testing. Cryptographic claims must reflect actual design, limitations and review status.

## Adding tests

- Add tests alongside the code they cover.
- Prefer testing behavior over implementation details.
- Keep fixtures free of real personal data, keys, or credentials.

## Current status

Automated test coverage is a work in progress. Contributions that add tests are
welcome.
