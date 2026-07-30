# Network Isolation

## Fraud Detection Platform

### Sprint 2 — Azure SQL Database Network Isolation

---

# Overview

This document describes the network isolation strategy implemented for the Azure SQL Database used by the Fraud Detection Platform.

The objective is to restrict database access to the application subnet created during Sprint 1 while preventing unauthorized external connections.

The solution reuses the Azure Virtual Network infrastructure previously deployed.

---

# Objective

The network isolation implementation satisfies the following objectives:

- Restrict database access to the application subnet.
- Reuse the Virtual Network created during Sprint 1.
- Allow application connectivity through Azure networking features.
- Reduce database exposure to unauthorized clients.
- Validate the network configuration.

---

# Network Architecture

The infrastructure used by the database is shown below.

```text
                    Azure Subscription
                           │
                    Resource Group
                 rg-centinela-prod
                           │
        ┌──────────────────┴──────────────────┐
        │                                     │
        ▼                                     ▼
   Azure App Service                  Azure SQL Server
        │                                     │
        │                                     ▼
        │                           Azure SQL Database
        │
        ▼
Virtual Network
10.10.0.0/24
        │
        ├──────────────┐
        │              │
        ▼              ▼
snet-app-prod     snet-data-prod
```

The Azure SQL Database is **not deployed inside the Virtual Network**.

Azure SQL Database is a Platform as a Service (PaaS) resource managed by Microsoft Azure.

Network access is controlled using Azure networking features.

---

# Network Components

## Virtual Network

The database reuses the Virtual Network created during Sprint 1.

Purpose:

- Network segmentation
- Secure communication
- Resource isolation

---

## Application Subnet

Application subnet:

```
snet-app-prod
```

Purpose:

- Azure App Service connectivity
- SQL Database access
- Service Endpoint configuration

---

## Data Subnet

Reserved for future infrastructure.

Examples:

- Private Endpoint
- Azure Cosmos DB
- Azure Storage Private Endpoint
- Azure SQL Private Endpoint

No workload currently uses this subnet.

---

# Service Endpoint Configuration

The application subnet has the following Service Endpoints enabled:

| Service |
|----------|
| Microsoft.Storage |
| Microsoft.Sql |

Verification command:

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

# Virtual Network Rule

A Virtual Network Rule was created on the Azure SQL Server.

Configuration:

| Property | Value |
|----------|-------|
| Rule Name | AllowAppSubnet |
| Status | Ready |
| Target Subnet | snet-app-prod |

Verification:

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

# Firewall Configuration

During development, a temporary firewall rule allows the developer workstation to connect to the Azure SQL Server.

This rule is required to:

- Execute deployment scripts.
- Validate the schema.
- Execute seed data.
- Perform manual verification.

The firewall restricts access to the developer public IP only.

Unauthorized public IP addresses cannot connect to the database.

---

# Security Validation

The following validations were successfully completed.

## Service Endpoint

Verified:

- Microsoft.Storage
- Microsoft.Sql

---

## Virtual Network Rule

Verified:

```
AllowAppSubnet

Ready
```

---

## Database Connectivity

Connection from the development workstation succeeded.

Validation query:

```sql
SELECT *
FROM dbo.status;
```

Result:

Six status records returned successfully.

---

## Firewall

Verified:

- Developer IP allowed.
- Unknown public IP addresses denied.

---

# Security Assessment

Current security controls:

- Azure SQL Authentication
- Azure SQL Firewall
- Microsoft.Sql Service Endpoint
- Virtual Network Rule
- Application subnet restriction

These controls significantly reduce unauthorized database access.

---

# Production Considerations

The current implementation corresponds to a development environment.

The Azure SQL Server still has:

```
Public Network Access

Enabled
```

This configuration allows the developer workstation to connect through an approved firewall rule.

A production environment should additionally implement:

- Disable Public Network Access.
- Azure Private Endpoint.
- Private DNS Zone.
- Microsoft Entra ID authentication.
- Azure Key Vault.
- Continuous monitoring.
- Azure Defender for SQL.

With those components, database traffic would remain entirely inside the Azure private network.

---

# Evidence

The following evidence was collected.

| Evidence | Description |
|-----------|-------------|
| subnet-service-endpoints.png | Microsoft.Storage and Microsoft.Sql enabled |
| sql-networking.png | SQL Server networking configuration |
| firewall-rules.png | Firewall configuration |
| vnet-rule.png | Virtual Network Rule successfully created |
| query-success.png | Database connectivity verification |

---

# Conclusion

The Azure SQL Database successfully reuses the Virtual Network deployed during Sprint 1.

Database access is restricted through Azure networking mechanisms by combining:

- Service Endpoints
- Virtual Network Rules
- Azure SQL Firewall

The configuration satisfies the Sprint 2 network isolation objectives while maintaining secure connectivity for development activities.

The infrastructure is prepared for future migration to Private Endpoints in a production environment.