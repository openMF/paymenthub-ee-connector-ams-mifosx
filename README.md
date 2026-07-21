# paymenthub-ee-connector-ams-mifosx

The connector that lets Payment Hub EE talk to the Mifos/Fineract core banking system (the Account Management System) to look up accounts and move money.

[![License](https://img.shields.io/badge/License-MPL--2.0-blue.svg)](LICENSE)

## What it does

- Looks up and validates accounts and clients in Fineract (by account, party id, or mobile number).
- Registers and removes interoperable party identifiers so a Fineract account can be reached from the payment switch.
- Gets a local quote from the core for an incoming transfer.
- Moves money against the core: deposits into savings accounts and repayments on loans.
- Reads account details and transaction history from the core.
- Supports more than one tenant (financial institution) in the same running service.

## How it fits into Payment Hub EE

Payment Hub EE runs payment flows as BPMN processes on Zeebe (Camunda). This connector is the bridge between that orchestration layer and the Fineract core banking backend. It runs Zeebe workers that pick up jobs from the running process (party lookup, quote, transfer, party registration) and turns them into calls to the Fineract API. The reply from Fineract is passed back into the process so the flow can continue. Without this connector, Payment Hub could route and orchestrate a payment but could not check or update accounts in the core.

## Tech stack

- Java 21
- Spring Boot 3.4
- Apache Camel 4 (routes plus the CXF JAX-RS REST client for the Fineract API)
- Zeebe (Camunda) job workers
- Gradle build
- Depends on `paymenthub-ee-bom` (shared version management) and `paymenthub-ee-core`

## Branches

- `dev` is the active development branch — all PRs should target `dev`.
- `main` holds released versions.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) and our [Code of Conduct](CODE_OF_CONDUCT.md).
