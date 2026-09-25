-- Default administrator: username "admin", password "admin123" (BCrypt, strength 10).
-- Change this password after the first deployment.
INSERT INTO users (username, password, role)
VALUES ('admin', '$2a$10$BDeWkKKtAKEtlF8rrcQ7Le0HWjs8RhG78bax0gkfenxkNopaDS126', 'ADMIN');
