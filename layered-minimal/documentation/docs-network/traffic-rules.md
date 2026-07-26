# Traffic Rules

## Purpose

This document describes the network traffic policy implemented during Sprint 1 of the Centinela project.

The objective is to protect the data layer by following the **Default Deny** principle, allowing only the minimum required communications.

---

# Security Principle

The network configuration follows the **Least Privilege** model.

All traffic is denied by default unless an explicit rule allows it.

At the end of Sprint 1, only the application subnet (`snet-app-prod`) is authorized to communicate with the Storage Account through Azure Service Endpoints.

---

# Traffic Rules

| # | Source | Destination | Port | Action | Justification |
|---|--------|-------------|------|--------|---------------|
| 1 | Internet | Storage Account | 443 (HTTPS) | x Deny | The Storage Firewall blocks all public access (`DefaultAction = Deny`). |
| 2 | snet-app-prod | Storage Account | 443 (HTTPS) | ✓Allow | Required for the future Azure App Service to securely access storage through a Service Endpoint. |
| 3 | snet-data-prod | Storage Account | 443 (HTTPS) | x Deny | Reserved for future SQL Database and Private Endpoints. No access is required during Sprint 1. |
| 4 | snet-process-prod | Storage Account | 443 (HTTPS) | x Deny | Reserved for Azure Functions and background processing planned for Sprint 3. |
| 5 | Any other subnet or public IP | Storage Account | 443 (HTTPS) | x Deny | Prevents unauthorized access and enforces the Least Privilege principle. |

---

# Current Communication Flow

The following communication path is expected after the application is deployed:

```text
Azure App Service
        │
        ▼
snet-app-prod
        │
        ▼
Service Endpoint
        │
        ▼
Storage Account
```

During Sprint 1, the application has not yet been deployed. Therefore, the Service Endpoint is configured but not yet used by any Azure resource.

---

# Default Deny Strategy

The Storage Account network configuration is based on the following settings:

| Setting | Value |
|---------|-------|
| Public Network Access | Selected Networks |
| Default Action | Deny |
| Authorized Subnet | snet-app-prod |
| Service Endpoint | Microsoft.Storage |

This configuration ensures that requests originating from the public Internet are rejected.

---

# Why Only One Authorized Subnet?

Only `snet-app-prod` was granted access because it will host the Azure App Service that interacts with the Storage Account.

The remaining subnets are intentionally blocked to reduce the attack surface and avoid granting unnecessary permissions before those services are deployed.

---

# Future Evolution

As new Azure resources are deployed in future sprints, the traffic policy will be reviewed and updated.

Potential future changes include:

- Azure SQL Database connectivity.
- Azure Functions communication.
- Private Endpoints.
- Network Security Groups (NSGs).
- Additional security controls.

Any new rule must follow the Least Privilege principle and be properly documented.

---

# Conclusion

The implemented traffic policy satisfies the Sprint 1 objective of isolating the data layer from the Internet.

Only explicitly authorized network traffic is permitted, while all other traffic is denied by default.
