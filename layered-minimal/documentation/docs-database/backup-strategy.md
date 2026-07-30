# Backup Strategy

## Fraud Detection Platform

### Sprint 2 — Azure SQL Database Backup Strategy

---

# Overview

This document describes the backup strategy implemented for the Azure SQL Database used by the Fraud Detection Platform.

The solution relies on Azure SQL Database built-in backup capabilities, eliminating the need for custom backup jobs or maintenance scripts.

The objective is to ensure database recoverability while keeping the solution simple and aligned with Azure platform best practices.

---

# Objectives

The backup strategy was designed to:

- Protect fraud investigation data.
- Minimize potential data loss.
- Support Point-in-Time Restore (PITR).
- Eliminate manual backup administration.
- Leverage Azure managed backup services.

---

# Database Information

| Property | Value |
|----------|-------|
| Database | sqldb-cases-prod |
| Service | Azure SQL Database |
| Service Tier | Basic |
| Status | Online |
| Backup Storage Redundancy | Zone-redundant |

---

# Azure Automatic Backups

Azure SQL Database automatically creates and manages database backups.

The platform performs:

- Full backups
- Differential backups
- Transaction log backups

These backups are maintained by Azure and are used to restore the database to a previous point in time.

No custom backup jobs, SQL Agent jobs, or maintenance plans are required.

---

# Backup Configuration

The current Azure SQL Database configuration provides the following backup capabilities.

| Backup Type | Configuration |
|-------------|---------------|
| Differential Backup | Every 12 hours |
| Full Backup | Automatically managed by Azure SQL Database |
| Transaction Log Backup | Automatically managed by Azure SQL Database |

Azure SQL Database internally manages the execution schedule for full and transaction log backups. The Azure Portal explicitly reports only the differential backup frequency for the current database configuration.

---

# Retention Policy

The database uses Azure SQL Database Point-in-Time Restore (PITR).

Current configuration:

| Property | Value |
|----------|-------|
| PITR Retention | 7 days |
| Weekly Long-Term Retention | Not configured |
| Monthly Long-Term Retention | Not configured |
| Yearly Long-Term Retention | Not configured |

The current retention period is appropriate for the project laboratory environment.

---

# Backup Storage Redundancy

The database uses:

**Zone-redundant backup storage**

This configuration provides:

- Backup copies stored across multiple availability zones.
- Increased resilience against datacenter failures.
- Azure-managed redundancy without additional administration.

---

# Recovery Point Objective (RPO)

Recovery Point Objective (RPO) defines the maximum acceptable amount of data that could be lost after a failure.

Current strategy:

| Metric | Value |
|---------|-------|
| RPO | Minutes |

Because Azure SQL Database automatically manages transaction log backups, the expected data loss is measured in minutes.

---

# Recovery Time Objective (RTO)

Recovery Time Objective (RTO) represents the estimated time required to restore database availability.

Current strategy:

| Metric | Value |
|---------|-------|
| RTO | Platform-managed |

Recovery time depends on:

- Database size.
- Azure platform recovery process.
- Restore operation type.

No manual recovery procedures are required for standard restore operations.

---

# Long-Term Retention

Long-Term Retention (LTR) is not configured for the current environment.

Current status:

| Policy | Status |
|---------|--------|
| Weekly LTR | Not configured |
| Monthly LTR | Not configured |
| Yearly LTR | Not configured |
| Time-based Immutability | Disabled |

This configuration is sufficient for the current development environment.

---

# Validation

The backup configuration was validated using Azure Portal and Azure CLI.

The following items were successfully verified:

- Database status is Online.
- Differential backup frequency is 12 hours.
- Point-in-Time Restore retention is 7 days.
- Backup storage redundancy is Zone-redundant.
- Long-Term Retention is not configured.

---

# Evidence

The following evidence supports this implementation.

| Evidence | Description |
|----------|-------------|
| database-overview.png | Azure SQL Database overview |
| backup-config.png | Backup configuration |
| backup-retention.png | Backup retention and storage redundancy |

---

# Production Considerations

The current configuration is appropriate for a laboratory environment.

A production deployment should additionally include:

- Long-Term Retention (LTR) policies.
- Periodic restore validation.
- Backup monitoring and alerting.
- Disaster recovery procedures.
- Geo-redundant backup validation when required by business continuity requirements.

Regular restore testing is recommended to verify backup integrity.

---

# Conclusion

Azure SQL Database provides a fully managed backup solution for the Fraud Detection Platform.

The implemented configuration provides:

- Automatic Azure-managed backups.
- Differential backups every 12 hours.
- Seven-day Point-in-Time Restore (PITR).
- Zone-redundant backup storage.
- Platform-managed recovery capabilities.

This backup strategy satisfies the Sprint 2 requirements while minimizing operational complexity and administrative overhead.