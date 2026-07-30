/*==============================================================================
    Project : Fraud Detection Platform
    Sprint  : Sprint 2
    File    : schema.sql

    Description:
    This script creates the relational database schema used to manage
    fraud investigation cases.

    The schema includes:
        - Case status catalog
        - Fraud cases
        - Analysts
        - Case assignments
        - Case resolutions
        - Audit history

    Target Database:
        Azure SQL Database (T-SQL)

    Author:
        Jainer Pabon

==============================================================================*/

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

/*==============================================================================
    TABLE: status

    Stores the catalog of valid statuses for fraud cases.
==============================================================================*/

CREATE TABLE dbo.status
(
    id_status INT IDENTITY(1,1) NOT NULL,

    name NVARCHAR(150) NOT NULL,

    CONSTRAINT PK_status
        PRIMARY KEY CLUSTERED (id_status),

    CONSTRAINT UQ_status_name
        UNIQUE (name)
);

GO

/*==============================================================================
    TABLE: cases

    Stores every fraud case detected by the platform.
==============================================================================*/

CREATE TABLE dbo.cases
(
    id_case INT IDENTITY(1,1) NOT NULL,

    id_status INT NOT NULL,

    id_transaction NVARCHAR(255) NOT NULL,

    score DECIMAL(5,2) NULL,

    opened_at DATETIME2(3)
        NOT NULL
        CONSTRAINT DF_cases_opened_at
        DEFAULT SYSUTCDATETIME(),

    created_at DATETIME2(3)
        NOT NULL
        CONSTRAINT DF_cases_created_at
        DEFAULT SYSUTCDATETIME(),

    updated_at DATETIME2(3) NULL,

    CONSTRAINT PK_cases
        PRIMARY KEY CLUSTERED (id_case),

    CONSTRAINT UQ_cases_transaction
        UNIQUE (id_transaction),

    CONSTRAINT CK_cases_score
        CHECK (score IS NULL OR score BETWEEN 0 AND 999.99)
);

GO

/*==============================================================================
    TABLE: analyst

    Stores the analysts responsible for investigating fraud cases.
==============================================================================*/

CREATE TABLE dbo.analyst
(
    id_analyst INT IDENTITY(1,1) NOT NULL,

    full_name NVARCHAR(100) NOT NULL,

    email NVARCHAR(255) NOT NULL,

    phone_number NVARCHAR(20) NULL,

    CONSTRAINT PK_analyst
        PRIMARY KEY CLUSTERED (id_analyst),

    CONSTRAINT UQ_analyst_email
        UNIQUE (email)
);

GO

/*==============================================================================
    TABLE: audit

    Stores every status transition performed on a fraud case.

    The audit history should never be automatically removed because it
    provides traceability for investigations.
==============================================================================*/

CREATE TABLE dbo.audit
(
    id_audit INT IDENTITY(1,1) NOT NULL,

    id_case INT NOT NULL,

    old_status_id INT NULL,

    new_status_id INT NOT NULL,

    changed_by NVARCHAR(100) NOT NULL,

    changed_at DATETIME2(3)
        NOT NULL
        CONSTRAINT DF_audit_changed_at
        DEFAULT SYSUTCDATETIME(),

    CONSTRAINT PK_audit
        PRIMARY KEY CLUSTERED (id_audit)
);

GO

/*==============================================================================
    TABLE: resolution

    Stores the final decision of each fraud investigation.

    One case can have only one final resolution.
==============================================================================*/

CREATE TABLE dbo.resolution
(
    id_resolution INT IDENTITY(1,1) NOT NULL,

    id_case INT NOT NULL,

    decision NVARCHAR(255) NOT NULL,

    notes NVARCHAR(MAX) NULL,

    resolved_at DATETIME2(3)
        NOT NULL
        CONSTRAINT DF_resolution_resolved_at
        DEFAULT SYSUTCDATETIME(),

    CONSTRAINT PK_resolution
        PRIMARY KEY CLUSTERED (id_resolution),

    CONSTRAINT UQ_resolution_case
        UNIQUE (id_case)
);

GO

/*==============================================================================
    TABLE: assignment

    Stores analyst assignments for fraud investigations.

    A fraud case may be assigned to one or more analysts over time.
==============================================================================*/

CREATE TABLE dbo.assignment
(
    id_assignment INT IDENTITY(1,1) NOT NULL,

    id_case INT NOT NULL,

    id_analyst INT NOT NULL,

    assigned_at DATETIME2(3)
        NOT NULL
        CONSTRAINT DF_assignment_assigned_at
        DEFAULT SYSUTCDATETIME(),

    CONSTRAINT PK_assignment
        PRIMARY KEY CLUSTERED (id_assignment),

    CONSTRAINT UQ_assignment_case_analyst
        UNIQUE (id_case, id_analyst)
);

GO

/*==============================================================================
    FOREIGN KEY CONSTRAINTS
==============================================================================*/

ALTER TABLE dbo.cases
ADD CONSTRAINT FK_cases_status
FOREIGN KEY (id_status)
REFERENCES dbo.status(id_status)
ON UPDATE NO ACTION
ON DELETE NO ACTION;

GO

ALTER TABLE dbo.audit
ADD CONSTRAINT FK_audit_case
FOREIGN KEY (id_case)
REFERENCES dbo.cases(id_case)
ON UPDATE NO ACTION
ON DELETE NO ACTION;

GO

ALTER TABLE dbo.audit
ADD CONSTRAINT FK_audit_old_status
FOREIGN KEY (old_status_id)
REFERENCES dbo.status(id_status)
ON UPDATE NO ACTION
ON DELETE NO ACTION;

GO

ALTER TABLE dbo.audit
ADD CONSTRAINT FK_audit_new_status
FOREIGN KEY (new_status_id)
REFERENCES dbo.status(id_status)
ON UPDATE NO ACTION
ON DELETE NO ACTION;

GO

ALTER TABLE dbo.resolution
ADD CONSTRAINT FK_resolution_case
FOREIGN KEY (id_case)
REFERENCES dbo.cases(id_case)
ON UPDATE NO ACTION
ON DELETE NO ACTION;

GO

ALTER TABLE dbo.assignment
ADD CONSTRAINT FK_assignment_case
FOREIGN KEY (id_case)
REFERENCES dbo.cases(id_case)
ON UPDATE NO ACTION
ON DELETE NO ACTION;

GO

ALTER TABLE dbo.assignment
ADD CONSTRAINT FK_assignment_analyst
FOREIGN KEY (id_analyst)
REFERENCES dbo.analyst(id_analyst)
ON UPDATE NO ACTION
ON DELETE NO ACTION;

GO

/*==============================================================================
    INDEXES

    SQL Server automatically creates indexes for Primary Keys and Unique
    Constraints. The following indexes optimize searches on Foreign Keys.
==============================================================================*/

CREATE NONCLUSTERED INDEX IX_cases_status
ON dbo.cases(id_status);

GO

CREATE NONCLUSTERED INDEX IX_audit_case
ON dbo.audit(id_case);

GO

CREATE NONCLUSTERED INDEX IX_audit_old_status
ON dbo.audit(old_status_id);

GO

CREATE NONCLUSTERED INDEX IX_audit_new_status
ON dbo.audit(new_status_id);

GO

CREATE NONCLUSTERED INDEX IX_assignment_analyst
ON dbo.assignment(id_analyst);

GO

/*==============================================================================
    End of schema.sql
==============================================================================*/
