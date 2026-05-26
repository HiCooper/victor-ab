-- V3: Link default admin user to ADMIN role
INSERT INTO rbac_user_role (user_id, role_id)
SELECT u.id, r.id
FROM victor_user u, rbac_role r
WHERE u.username = 'admin' AND r.name = 'ADMIN'
ON DUPLICATE KEY UPDATE user_id = user_id;
