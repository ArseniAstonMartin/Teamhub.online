# Privacy Policy — Stub

<!-- TO-DO: privacy policy at https://teamhub.online/privacy should reference the CCPA data export
endpoint (/api/v1/compliance/export) and erasure endpoint (/api/v1/compliance/erase-customer/{id})
per PRD §24.5 -->

This document is a placeholder for the Teamhub privacy policy required by CCPA (PRD §24.5).

## CCPA Data Rights

Teamhub provides the following endpoints to support CCPA data subject rights:

- **Data Export** — `GET /api/v1/compliance/export` (Admin only): returns a ZIP archive of all
  tenant data (users, jobs, customers, audit log) in machine-readable JSON format.

- **Customer Erasure** — `POST /api/v1/compliance/erase-customer/{id}` (Admin only): anonymises a
  customer's personally identifiable information (name, phone, email, address) while preserving
  the record for referential integrity. The operation is irreversible and audit-logged.

Both endpoints require the `ADMIN` role. Each invocation is recorded in the audit log.

> TO-DO: Replace this stub with the full privacy policy before public launch. Ensure legal review
> of the CCPA disclosure language, data retention periods, and opt-out mechanisms.
