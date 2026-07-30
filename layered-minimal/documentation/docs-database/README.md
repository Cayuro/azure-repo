# Fraud Detection Platform — Azure SQL Database

This directory contains all the database artifacts required to deploy and validate the relational case management database used by the Fraud Detection Platform.

The solution is designed for **Azure SQL Database** and includes:

- Database schema
- Seed data
- Network isolation documentation
- Deployment guide
- Deployment evidence

---

# Objectives

The database supports the fraud investigation process by storing:

- Fraud cases
- Case status catalog
- Analyst assignments
- Case resolutions
- Audit history

The original financial transactions are **not stored** in this database.

This database is dedicated exclusively to fraud case management.

---

# Project Structure

```text
docs-database/
│
├── README.md
├── deployment-guide.md
├── network-isolation.md
├── schema.sql
├── seed.sql
│
└── evidence/
    ├── database-created.png
    ├── schema-deployed.png
    ├── seed-executed.png
    ├── subnet-service-endpoints.png
    ├── sql-networking.png
    ├── vnet-rule.png
    ├── firewall-rules.png
    └── query-success.png
```

---

# Database Architecture

The relational model contains six tables.

| Table | Description |
|---------|-------------|
| status | Catalog of fraud case states |
| cases | Fraud cases detected by the platform |
| analyst | Fraud analysts responsible for investigations |
| assignment | Relationship between analysts and cases |
| resolution | Final decision for each fraud case |
| audit | Complete history of case status changes |

---

# Entity Relationship Model

Relationships implemented:

```
status
   │
   └──────< cases
               │
               ├──────< assignment >────── analyst
               │
               ├──────< audit
               │
               └────── resolution
```

---

# Deployment Files

## schema.sql

Creates:

- Tables
- Primary keys
- Foreign keys
- Constraints
- Indexes

Execution:

```text
schema.sql
```

---

## seed.sql

Populates mandatory catalog data.

Currently inserts:

- Open
- In Review
- Escalated
- Confirmed Fraud
- False Positive
- Closed

The script is idempotent and can be executed multiple times safely.

Execution:

```text
seed.sql
```

---

# Deployment Order

Run the scripts in the following order:

```
1. schema.sql
2. seed.sql
```

---

# Azure Resources

The solution was deployed using:

| Resource | Service |
|-----------|----------|
| Azure SQL Server | Azure SQL |
| Azure SQL Database | Basic Tier |
| Azure Virtual Network | Existing (Sprint 1) |
| Service Endpoint | Microsoft.Sql |
| Virtual Network Rule | AllowAppSubnet |

---

# Network Isolation

The database is protected using Azure networking features.

Implemented:

- Microsoft.Sql Service Endpoint
- Virtual Network Rule
- Azure SQL Firewall
- Application subnet restriction

More details are available in:

```
network-isolation.md
```

---

# Validation

The deployment was validated by executing:

```sql
SELECT *
FROM dbo.status;
```

Expected result:

| id_status | name |
|------------|------|
| 1 | Open |
| 2 | In Review |
| 3 | Escalated |
| 4 | Confirmed Fraud |
| 5 | False Positive |
| 6 | Closed |

---

# Evidence

Deployment evidence includes:

- Azure SQL Database creation
- Successful schema deployment
- Seed execution
- Microsoft.Sql Service Endpoint
- Virtual Network Rule
- SQL Server Networking configuration
- Firewall configuration
- Successful SQL query execution

Evidence files are stored in:

```
evidence/
```

---

# Production Considerations

This project was developed in a laboratory environment.

For development purposes, the Azure SQL Server keeps a temporary firewall rule allowing the developer workstation to connect.

A production deployment should additionally include:

- Public Network Access disabled
- Private Endpoint
- Private DNS Zone
- Azure Key Vault for secrets
- Microsoft Entra ID authentication
- Network Security monitoring
- Automated backup validation

---

# Related Documentation

| Document | Description |
|-----------|-------------|
| deployment-guide.md | Complete deployment procedure |
| network-isolation.md | Network security configuration |
| schema.sql | Database schema |
| seed.sql | Mandatory catalog data |

---

# Technologies

- Azure SQL Database
- Azure SQL Server
- Azure Virtual Network
- Azure CLI
- T-SQL
- Azure Portal

---

# Sprint

Sprint 2

Task 1 — Fraud Case Database

Task 2 — Network Isolation

Task 3 — Backup Strategy