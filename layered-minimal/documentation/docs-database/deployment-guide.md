# Deployment Guide

## Fraud Detection Platform

### Sprint 2 — Azure SQL Database Deployment

---

# Overview

This guide explains how to deploy the relational database used by the Fraud Detection Platform on Microsoft Azure.

The deployment includes:

- Azure SQL Server
- Azure SQL Database (Basic Tier)
- Database schema
- Seed data
- Network isolation
- Deployment validation

This guide assumes the Azure infrastructure created during Sprint 1 already exists.

---

# Prerequisites

Before starting, verify the following resources already exist.

## Azure Resources

- Resource Group
- Virtual Network
- Application Subnet
- Azure Subscription

Required resources:

| Resource | Status |
|----------|--------|
| Resource Group | Existing |
| Virtual Network | Existing |
| snet-app-prod | Existing |
| Azure CLI | Installed |
| Azure Account | Logged in |

---

# Step 1 — Create the Azure SQL Server

Create an Azure SQL logical server.

Required information:

- Server name
- Region
- Administrator username
- Administrator password

Example:

```
sql-centinela-prod
```

---

# Step 2 — Create the Azure SQL Database

Create a database inside the SQL Server.

Configuration used for this project:

| Property | Value |
|----------|-------|
| Service | Azure SQL Database |
| Tier | Basic |
| Backup Storage | Default |
| Public Access | Enabled (development only) |

---

# Step 3 — Configure Firewall

Allow the developer workstation.

Example:

```bash
az sql server firewall-rule create \
    --resource-group <resource-group> \
    --server <sql-server> \
    --name AllowMyLaptop \
    --start-ip-address <your-ip> \
    --end-ip-address <your-ip>
```

Verify:

```bash
az sql server firewall-rule list \
    --resource-group <resource-group> \
    --server <sql-server>
```

---

# Step 4 — Enable Microsoft.Sql Service Endpoint

Reuse the Virtual Network created during Sprint 1.

Enable:

- Microsoft.Storage
- Microsoft.Sql

Example:

```bash
az network vnet subnet update \
    --resource-group <resource-group> \
    --vnet-name <vnet-name> \
    --name snet-app-prod \
    --service-endpoints Microsoft.Storage Microsoft.Sql
```

Verify:

```bash
az network vnet subnet show \
    --resource-group <resource-group> \
    --vnet-name <vnet-name> \
    --name snet-app-prod \
    --query "serviceEndpoints[].service"
```

Expected result:

```
Microsoft.Storage
Microsoft.Sql
```

---

# Step 5 — Create the Virtual Network Rule

Associate the SQL Server with the application subnet.

Example:

```bash
az sql server vnet-rule create \
    --resource-group <resource-group> \
    --server <sql-server> \
    --name AllowAppSubnet \
    --subnet <subnet-resource-id>
```

Verify:

```bash
az sql server vnet-rule list \
    --resource-group <resource-group> \
    --server <sql-server>
```

Expected state:

```
Ready
```

---

# Step 6 — Connect to the Database

Use one of the following tools:

- Azure Data Studio
- Visual Studio Code
- sqlcmd

Connect using:

- Server
- Database
- SQL Login

---

# Step 7 — Deploy schema.sql

Execute:

```
schema.sql
```

The script creates:

- Tables
- Constraints
- Foreign Keys
- Indexes

---

# Step 8 — Deploy seed.sql

Execute:

```
seed.sql
```

The script inserts the mandatory catalog data.

Current values:

- Open
- In Review
- Escalated
- Confirmed Fraud
- False Positive
- Closed

The script is idempotent.

---

# Step 9 — Validate the Deployment

Verify the database.

## Verify tables

```sql
SELECT TABLE_NAME
FROM INFORMATION_SCHEMA.TABLES
ORDER BY TABLE_NAME;
```

Expected:

- analyst
- assignment
- audit
- cases
- resolution
- status

---

## Verify catalog

```sql
SELECT *
FROM dbo.status;
```

Expected:

Six rows.

---

## Verify Foreign Keys

```sql
SELECT
    fk.name,
    OBJECT_NAME(parent_object_id)
FROM sys.foreign_keys fk;
```

---

## Verify indexes

```sql
SELECT
    name
FROM sys.indexes
WHERE name LIKE 'IX_%';
```

---

# Step 10 — Verify Network Isolation

Open Azure Portal.

Navigate to:

```
SQL Server

↓

Networking
```

Verify:

- Public Network Access
- Firewall Rules
- Virtual Network Rules

The Virtual Network Rule must be:

```
AllowAppSubnet

Ready
```

Verify the application subnet contains:

- Microsoft.Storage
- Microsoft.Sql

---

# Deployment Evidence

Capture evidence for:

- Azure SQL Server
- Azure SQL Database
- Database deployment
- Service Endpoint
- Virtual Network Rule
- Firewall configuration
- Successful SQL query

Store all screenshots inside:

```
documentation/docs-database/evidence/
```

---

# Production Considerations

This deployment was performed in a development environment.

A production deployment should additionally include:

- Public Network Access disabled
- Private Endpoint
- Private DNS Zone
- Microsoft Entra ID authentication
- Azure Key Vault
- Continuous backup validation
- Monitoring with Azure Monitor
- Diagnostic Logs enabled

---

# Deployment Summary

The deployment provides:

- Azure SQL Database
- Relational schema
- Mandatory catalog data
- Network isolation using Virtual Network Rules
- Database validation
- Deployment evidence

The solution is ready for application integration.