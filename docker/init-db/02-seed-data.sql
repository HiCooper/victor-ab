-- GateFlow seed data — default admin user and roles
-- Password: admin123 (BCrypt, cost 10)
-- Generate a new hash: python3 -c "import bcrypt; print(bcrypt.hashpw(b'YOUR_PASSWORD', bcrypt.gensalt(10)).decode())"

-- Default roles
INSERT IGNORE INTO rbac_role (id, name, description) VALUES
(1, 'ADMIN', 'Administrator — full access to all experiments, layers, and settings'),
(2, 'OPERATOR', 'Operator — can create and manage experiments, view reports'),
(3, 'VIEWER', 'Viewer — read-only access to experiment results'),
(4, 'SDK_CLIENT', 'SDK client for bucketing/config/event APIs');

-- Default admin user (password: admin123)
INSERT IGNORE INTO victor_user (username, password_hash, email, enabled) VALUES
('admin', '$2b$10$bHcDaA9ZIQo.B9dtLYu3mOxrAVOLzz70v5lJdOzdbaE71SHwLDrke', 'admin@gateflow.dev', 1);

-- Assign ADMIN role to admin user
INSERT IGNORE INTO rbac_user_role (user_id, role_id)
SELECT u.id, r.id FROM victor_user u, rbac_role r
WHERE u.username = 'admin' AND r.name = 'ADMIN';
