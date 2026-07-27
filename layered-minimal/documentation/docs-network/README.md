# Sprint 1 - Private Network and Base Infrastructure

## Project

**Proyecto Centinela**

## Sprint Objective

The objective of Sprint 1 was to build the foundational cloud infrastructure required for the Centinela project. The focus was to isolate the data layer from the Internet, design the virtual network architecture, and prepare the environment for future application deployment.

The infrastructure was implemented using Microsoft Azure following the principle of **Default Deny** and the **Least Privilege** security model.

---

# Infrastructure Overview

The following Azure resources were created during this sprint:

| Resource | Name |
|----------|------|
| Resource Group | rg-centinela-prod |
| Region | West US |
| Virtual Network | vnet-centinela-prod-v3 |
| Address Space | 10.10.0.0/16 |
| Storage Account | sttransaccionesfase1ch1 |

---

# Network Topology

The virtual network was divided into dedicated subnets to separate responsibilities and facilitate future scaling.

| Subnet | Address Prefix | Purpose |
|---------|---------------|----------|
| snet-app-prod | 10.10.1.0/27 | Azure App Service VNet Integration |
| snet-data-prod | 10.10.2.0/27 | Reserved for Azure SQL Database and Private Endpoints |
| snet-process-prod | 10.10.3.0/27 | Reserved for Azure Functions and background processing |

Each subnet uses a **/27 prefix**, providing 32 IP addresses (27 usable after Azure reserves five IP addresses).

This sizing satisfies Azure App Service VNet Integration requirements while leaving room for future project growth.

---

# Storage Isolation

The Storage Account was configured following a zero-trust approach.

Configuration summary:

- Storage Firewall enabled
- Default Action = Deny
- Service Endpoint enabled only on **snet-app-prod**
- Public Internet access blocked
- Only resources deployed inside **snet-app-prod** will be authorized to access the Storage Account.

---

# Deliverables

This sprint includes the following documentation:

| Document | Description |
|-----------|-------------|
| network-design.md | Network architecture and design decisions |
| traffic-rules.md | Traffic policy following the Default Deny principle |
| isolation-test.md | Validation that the Storage Account is isolated from the Internet |
| diagrams/network.png | Network architecture diagram |
| evidence/ | CLI and Azure Portal validation screenshots |

---

# Architecture Principles

The infrastructure was designed according to the following principles:

- Network segmentation
- Least Privilege
- Default Deny
- Secure communication over HTTPS
- Infrastructure prepared for Sprint 2 and Sprint 3

---

# Sprint Result

At the end of Sprint 1 the infrastructure provides:

- Secure Virtual Network
- Dedicated subnet segmentation
- Data layer isolated from the Internet
- Storage Firewall configured
- Service Endpoint configured
- Foundation prepared for application deployment in the next sprint
