-- V2: Create permissions tables
CREATE TABLE permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    category VARCHAR(50)
);

CREATE TABLE role_permissions (
    role VARCHAR(20) NOT NULL CHECK (role IN ('PATIENT', 'EMPLOYEE', 'ADMIN')),
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role, permission_id)
);
