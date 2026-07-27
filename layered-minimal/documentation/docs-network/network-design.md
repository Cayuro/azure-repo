# Network Design

## Purpose

The objective of the network design is to provide a secure, scalable, and maintainable infrastructure for the Centinela project.

The network architecture was designed to isolate the data layer from the Internet while preparing the environment for future application components that will be implemented during Sprint 2 and Sprint 3.

The design follows Microsoft's recommended practices for network segmentation and the security principle of **Least Privilege**.

---

# Network Architecture

The infrastructure created during Sprint 1 consists of the following Azure resources:

| Resource | Name |
|----------|------|
| Resource Group | rg-centinela-prod |
| Region | West US |
| Virtual Network | vnet-centinela-prod-v3 |
| Address Space | 10.10.0.0/16 |
| Storage Account | sttransaccionesfase1ch1 |

---

# Address Space

The Virtual Network uses the following address space:

```
10.10.0.0/16
```

This range provides enough address capacity for future project growth without requiring redesign of the virtual network.

Using a single private address space also simplifies routing and subnet management.

---

# Subnet Design

The Virtual Network was divided into three dedicated subnets.

| Subnet | Address Prefix | Purpose | Sprint |
|---------|---------------|----------|---------|
| snet-app-prod | 10.10.1.0/27 | Azure App Service VNet Integration | Sprint 2 |
| snet-data-prod | 10.10.2.0/27 | Azure SQL Database / Private Endpoints | Future |
| snet-process-prod | 10.10.3.0/27 | Azure Functions and background processing | Sprint 3 |

Each subnet has a dedicated responsibility to reduce coupling between services and improve security.

---

# Why /27?

Each subnet was created using a **/27** prefix.

A /27 subnet provides:

- 32 total IP addresses
- 27 usable IP addresses (Azure reserves five IP addresses in every subnet)

This size satisfies the minimum subnet size recommended for Azure App Service VNet Integration while leaving sufficient capacity for future scaling.

---

# Service Endpoint Design

Sprint 1 uses **Virtual Network Service Endpoints** instead of Private Endpoints.

The Service Endpoint was enabled only on:

```
snet-app-prod
```

This configuration allows future application services deployed inside the authorized subnet to securely access Azure Storage using the Microsoft backbone network.

The remaining subnets do not have Service Endpoints enabled because they are reserved for future infrastructure components.

---

# Storage Network Security

The Storage Account was configured with the following network policy:

| Configuration | Value |
|--------------|-------|
| Public Network Access | Selected Networks |
| Default Action | Deny |
| Authorized Subnet | snet-app-prod |
| Service Endpoint | Microsoft.Storage |

This configuration prevents direct access to the Storage Account from the public Internet.

Only resources integrated with the authorized subnet will be able to access the data layer.

---

# Security Design Decisions

The following design decisions were adopted during Sprint 1.

| Decision | Justification |
|----------|---------------|
| Dedicated Virtual Network | Logical isolation of cloud resources |
| Three dedicated subnets | Separation of responsibilities |
| /27 subnet size | Supports Azure App Service integration and future growth |
| Default Deny | Blocks unauthorized access by default |
| Service Endpoint | Secure Storage access without additional cost |
| West US region | Project deployment region |
| Reserved subnets | Avoid future network redesign |

---

# Scalability

The network was intentionally designed with additional subnets that are not yet in use.

This approach allows future Azure resources to be deployed without modifying the network topology.

Expected evolution:

| Sprint | Planned Component |
|---------|-------------------|
| Sprint 2 | Azure App Service |
| Sprint 2 | Azure SQL Database |
| Sprint 3 | Azure Functions |
| Sprint 3 | Background processing services |

---

# Out of Scope

The following components are outside the scope of Sprint 1:

- Azure SQL Database deployment
- Azure App Service deployment
- Azure Functions deployment
- Private Endpoints
- Network Security Groups (NSGs)
- Azure Firewall
- Azure Monitor
- Log Analytics
- Diagnostic Settings

These services will be evaluated and implemented in future sprints according to project requirements.

---

# Conclusion

Sprint 1 establishes the networking foundation of the Centinela project.

The implemented design provides:

- Secure network segmentation
- Data layer isolation
- Controlled access to Azure Storage
- Infrastructure prepared for future expansion
- A scalable architecture aligned with Azure networking best practices
