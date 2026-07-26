# Deployment Guide

## Purpose

This document describes the complete deployment process performed during Sprint 1 to build the networking foundation of the Centinela project.

The guide includes every Azure CLI command required to reproduce the infrastructure, configure secure network access, and validate that the Storage Account is isolated from the public Internet.

---

# Prerequisites

Before starting, verify the following requirements.

- Azure CLI installed.
- Azure subscription with Contributor permissions.
- Authenticated Azure session.
- Azure subscription selected.

Login:

```bash
az login
```

Verify the active subscription:

```bash
az account show --output table
```

---

# Deployment Variables

The deployment uses the following variables.

```bash
RG="rg-centinela-prod"

LOCATION="chilecentral"

VNET="vnet-centinela-prod-v3"

ADDRESS_SPACE="10.10.0.0/16"

SUBNET_APP="snet-app-prod"
SUBNET_DATA="snet-data-prod"
SUBNET_PROCESS="snet-process-prod"

STORAGE="sttransaccionesfase1ch1"
```

---

# Step 1 – Create the Resource Group

```bash
az group create \
    --name $RG \
    --location $LOCATION
```

Verification

```bash
az group show \
    --name $RG \
    --output table
```

Expected Result

- Resource Group exists.
- Region is West US.

---

# Step 2 – Create the Virtual Network

```bash
az network vnet create \
    --resource-group $RG \
    --name $VNET \
    --location $LOCATION \
    --address-prefixes $ADDRESS_SPACE
```

Verification

```bash
az network vnet show \
    --resource-group $RG \
    --name $VNET \
    --output table
```

Expected Result

- Virtual Network created successfully.
- Address Space: 10.10.0.0/16.

---

# Step 3 – Create the Application Subnet

```bash
az network vnet subnet create \
    --resource-group $RG \
    --vnet-name $VNET \
    --name $SUBNET_APP \
    --address-prefixes 10.10.1.0/27
```

---

# Step 4 – Create the Data Subnet

```bash
az network vnet subnet create \
    --resource-group $RG \
    --vnet-name $VNET \
    --name $SUBNET_DATA \
    --address-prefixes 10.10.2.0/27
```

---

# Step 5 – Create the Processing Subnet

```bash
az network vnet subnet create \
    --resource-group $RG \
    --vnet-name $VNET \
    --name $SUBNET_PROCESS \
    --address-prefixes 10.10.3.0/27
```

---

# Step 6 – Verify Subnets

```bash
az network vnet subnet list \
    --resource-group $RG \
    --vnet-name $VNET \
    --output table
```

Expected Result

Three subnets are displayed.

- snet-app-prod
- snet-data-prod
- snet-process-prod

---

# Step 7 – Enable the Service Endpoint

Only the application subnet requires access to Azure Storage.

```bash
az network vnet subnet update \
    --resource-group $RG \
    --vnet-name $VNET \
    --name $SUBNET_APP \
    --service-endpoints Microsoft.Storage
```

Verification

```bash
az network vnet subnet show \
    --resource-group $RG \
    --vnet-name $VNET \
    --name $SUBNET_APP \
    --query serviceEndpoints
```

Expected Result

```
Microsoft.Storage
```

---

# Step 8 – Configure the Storage Firewall

This configuration was completed through the Azure Portal.

Configuration

- Public Network Access → Selected Networks
- Authorized subnet → snet-app-prod
- Default Action → Deny

Verification

```bash
az storage account show \
    --resource-group $RG \
    --name $STORAGE \
    --query "networkRuleSet"
```

Expected Result

- DefaultAction = Deny
- Authorized subnet = snet-app-prod
- No public IP rules.

---

# Step 9 – Validate Storage Isolation

Verify the Storage Firewall configuration.

```bash
az storage account show \
    --resource-group $RG \
    --name $STORAGE \
    --query "networkRuleSet"
```

Expected Result

```
DefaultAction = Deny
```

---

Attempt to list Storage containers.

```bash
az storage container list \
    --account-name $STORAGE \
    --auth-mode login
```

Expected Result

```
The request may be blocked by network rules of storage account.
```

This confirms that the Storage Account cannot be accessed from outside the authorized Virtual Network.

---

Retrieve the public Blob endpoint.

```bash
az storage account show \
    --resource-group $RG \
    --name $STORAGE \
    --query "primaryEndpoints.blob" \
    --output tsv
```

Expected Result

```
https://sttransaccionesfase1ch1.blob.core.windows.net/
```

Although the endpoint exists, access is restricted by the Storage Firewall.

---

# Azure Portal Verification

After completing the deployment, verify the following configuration.

## Virtual Network

Azure Portal

Resource Group

↓

Virtual Network

Verify:

- Address Space
- Three subnets
- Address prefixes

---

## Service Endpoint

Azure Portal

Virtual Network

↓

Subnet

↓

Service Endpoints

Verify:

- Microsoft.Storage enabled only for **snet-app-prod**

---

## Storage Networking

Azure Portal

Storage Account

↓

Networking

Verify:

- Public Network Access = Selected Networks
- Authorized subnet = snet-app-prod
- Default Action = Deny

---

## Storage Containers

Azure Portal

Storage Account

↓

Containers

Verify:

Opening a container from outside the authorized subnet returns an authorization error.

---

# Evidence

The following evidence is included in the repository.

| Evidence | Description |
|----------|-------------|
| cli-firewall.png | Storage Firewall configuration obtained with Azure CLI |
| cli-storage-access-blocked.png | Azure CLI showing blocked access to Storage |
| portal-networking.png | Storage networking configuration |
| portal-storage-access-denied.png | Azure Portal authorization error |
| network-diagram.png | Network architecture diagram |

---

# Final Architecture

The infrastructure deployed during Sprint 1 consists of:

- One Resource Group
- One Virtual Network
- Three dedicated subnets
- One Azure Storage Account
- One Service Endpoint
- Storage Firewall configured with Default Deny
- Network prepared for Sprint 2 and Sprint 3

---

# Conclusion

Sprint 1 successfully established the networking foundation of the Centinela project.

The deployed infrastructure isolates the data layer from the public Internet while preparing the environment for future application components.

All deployment steps are reproducible using the commands documented in this guide.
