-- V7: Seed permissions and role_permissions

-- Base permissions
INSERT INTO permissions (code, description, category) VALUES
    ('OWN_PROFILE_VIEW', 'View own profile', 'profile'),
    ('OWN_PROFILE_EDIT', 'Edit own contact info', 'profile'),
    ('OWN_RESULTS_VIEW', 'View own lab results', 'results'),
    ('PASSWORD_CHANGE', 'Change own password', 'profile'),
    ('PATIENT_LIST', 'List all patients', 'patients'),
    ('PATIENT_VIEW', 'View patient details', 'patients'),
    ('PATIENT_CREATE', 'Create patient accounts', 'patients'),
    ('RESULTS_LIST', 'List all results', 'results'),
    ('RESULTS_UPLOAD', 'Upload lab results', 'results'),
    ('EMPLOYEE_MANAGE', 'Manage employees', 'admin'),
    ('GROUP_MANAGE', 'Manage groups and permissions', 'admin'),
    ('AUDIT_VIEW', 'View audit logs', 'admin'),
    ('SETTINGS_MANAGE', 'Manage lab settings', 'admin');

-- PATIENT role permissions
INSERT INTO role_permissions (role, permission_id)
SELECT 'PATIENT', id FROM permissions WHERE code IN (
    'OWN_PROFILE_VIEW', 'OWN_PROFILE_EDIT', 'OWN_RESULTS_VIEW', 'PASSWORD_CHANGE'
);

-- EMPLOYEE role permissions (same base as patient — groups extend)
INSERT INTO role_permissions (role, permission_id)
SELECT 'EMPLOYEE', id FROM permissions WHERE code IN (
    'OWN_PROFILE_VIEW', 'OWN_PROFILE_EDIT', 'OWN_RESULTS_VIEW', 'PASSWORD_CHANGE'
);

-- ADMIN role permissions (all)
INSERT INTO role_permissions (role, permission_id)
SELECT 'ADMIN', id FROM permissions;
