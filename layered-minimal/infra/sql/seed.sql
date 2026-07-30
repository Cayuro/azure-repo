/*==============================================================================
    Project : Fraud Detection Platform
    Sprint  : Sprint 2
    File    : seed.sql

    Description:
    Mandatory catalog data. Must run in every environment
    (development, QA, and production).

    Execution order:
        1. schema.sql
        2. seed.sql
        3. sample-data.sql (development / QA only)

    This script is idempotent: it can be run multiple times without
    creating duplicates or violating UNIQUE constraints.

    Target Database:
        Azure SQL Database (T-SQL)
==============================================================================*/

SET NOCOUNT ON;
GO

/*==============================================================================
    Seed the Status catalog.

    These records are required because every fraud case must reference
    a valid status through the foreign key (dbo.cases.id_status).
==============================================================================*/

MERGE dbo.status AS target
USING (VALUES
    (N'Open'),
    (N'In Review'),
    (N'Escalated'),
    (N'Confirmed Fraud'),
    (N'False Positive'),
    (N'Closed')
) AS source (name)
ON target.name = source.name
WHEN NOT MATCHED THEN
    INSERT (name) VALUES (source.name);

GO
/*==============================================================================
    The database is now ready to accept fraud case records.

    End of seed.sql
==============================================================================*/
