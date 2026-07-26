# Storage Isolation Validation

## Purpose

The objective of this validation is to demonstrate that the Storage Account deployed during Sprint 1 is protected from unauthorized access from the public Internet.

The validation confirms that the implemented network configuration successfully isolates the data layer while allowing future access only from authorized Azure resources inside the configured Virtual Network.

---

# Validation Environment

| Property | Value |
|----------|------|
| Cloud Provider | Microsoft Azure |
| Region | West US |
| Resource Group | rg-centinela-prod |
| Virtual Network | vnet-centinela-prod-v3 |
| Storage Account | sttransaccionesfase1ch1 |
| Authorized Subnet | snet-app-prod |

---

# Implemented Network Configuration

The Storage Account was configured using the following security settings.

| Configuration | Value |
|--------------|-------|
| Public Network Access | Selected Networks |
| Default Action | Deny |
| Authorized Subnet | snet-app-prod |
| Service Endpoint | Microsoft.Storage |

This configuration ensures that only resources deployed inside the authorized subnet will be able to communicate with the Storage Account.

---

# Validation 1 – Verify Storage Firewall Configuration

The following Azure CLI command was executed.

```bash
az storage account show \
  --resource-group $RG \
  --name $STORAGE \
  --query "networkRuleSet"
```

Result:

- Default Action = Deny
- No public IP addresses are authorized.
- Only the subnet **snet-app-prod** is configured as an allowed virtual network.

This confirms that the Storage Firewall is correctly configured.

---

# Validation 2 – Attempt to List Storage Containers

The following command was executed from a workstation outside the Virtual Network.

```bash
az storage container list \
  --account-name $STORAGE \
  --auth-mode login
```

Result:

```
The request may be blocked by network rules of storage account.
```

Interpretation:

Authentication was successful, but network access was denied because the request originated outside the authorized subnet.

This confirms that Azure Storage Firewall is enforcing the configured network policy.

---

# Validation 3 – Verify Public Endpoint

The public Blob endpoint was obtained using Azure CLI.

```bash
az storage account show \
  --resource-group $RG \
  --name $STORAGE \
  --query "primaryEndpoints.blob" \
  --output tsv
```

Returned endpoint:

```
https://sttransaccionesfase1ch1.blob.core.windows.net/
```

The endpoint exists because Azure Storage exposes a public service endpoint.

However, network access is controlled by the Storage Firewall.

---

# Validation Summary

| Validation | Result |
|------------|--------|
| Storage Firewall configured | ✅ |
| Default Action = Deny | ✅ |
| Service Endpoint configured | ✅ |
| Authorized subnet configured | ✅ |
| Public Internet access blocked | ✅ |

---

# Evidence

The following screenshots provide evidence of the implemented configuration.

- Azure Portal – Networking configuration
- Azure Portal – Storage Firewall
- Azure CLI – Network Rule Set
- Azure CLI – Blocked container access

The images are available in the **evidence/** directory.

---

# Conclusion

Sprint 1 successfully achieved the objective of isolating the data layer from the public Internet.

The implemented configuration ensures that the Storage Account cannot be accessed from unauthorized public networks.

Future Azure resources deployed inside **snet-app-prod** will be able to access the Storage Account through the configured Service Endpoint.
