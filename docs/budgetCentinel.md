 az consumption budget show --budget-name budgetCentinel
Command group 'consumption' is in preview and under development. Reference and support levels: https://aka.ms/CLI_refstatus
{
  "amount": "60.0",
  "category": "Cost",
  "currentSpend": {
    "amount": "0.0",
    "unit": "USD"
  },
  "eTag": "\"1dd15f06b2c9ce3\"",
  "id": "/subscriptions/ee586a6c-da64-4247-9146-9ce13328e02f/resourceGroups/rg-centinela-prod/providers/Microsoft.Consumption/budgets/budgetCentinel",
  "name": "budgetCentinel",
  "notifications": {
    "actual_GreaterThan_50_Percent": {
      "contactEmails": [
        "antonio.pulgarin97@gmail.com"
      ],
      "contactGroups": [],
      "contactRoles": [],
      "enabled": true,
      "operator": "GreaterThan",
      "threshold": "50.0"
    },
    "actual_GreaterThan_70_Percent": {
      "contactEmails": [
        "antonio.pulgarin97@gmail.com"
      ],
      "contactGroups": [],
      "contactRoles": [],
      "enabled": true,
      "operator": "GreaterThan",
      "threshold": "70.0"
    },
    "actual_GreaterThan_90_Percent": {
      "contactEmails": [
        "antonio.pulgarin97@gmail.com"
      ],
      "contactGroups": [],
      "contactRoles": [],
      "enabled": true,
      "operator": "GreaterThan",
      "threshold": "90.0"
    },
    "forecasted_GreaterThan_100_Percent": {
      "contactEmails": [
        "antonio.pulgarin97@gmail.com"
      ],
      "contactGroups": [],
      "contactRoles": [],
      "enabled": true,
      "operator": "GreaterThan",
      "threshold": "100.0"
    }
  },
  "resourceGroup": "rg-centinela-prod",
  "timeGrain": "Monthly",
  "timePeriod": {
    "endDate": "2028-06-30T00:00:00Z",
    "startDate": "2026-07-01T00:00:00Z"
  },
  "type": "Microsoft.Consumption/budgets"
}
