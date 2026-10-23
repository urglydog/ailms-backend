CREATE TABLE system_configs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(50) NOT NULL UNIQUE,
    config_value VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL
);

INSERT INTO system_configs (config_key, config_value, description) VALUES
  ('QUOTA_DUB_STUDENT', '15', 'Lồng tiếng / học viên / ngày (BR-DUB-06)'),
  ('QUOTA_DUB_INSTRUCTOR', '30', 'Lồng tiếng / giảng viên / ngày (BR-DUB-06)'),
  ('QUOTA_MATERIALS', '6', 'Sinh học liệu / ngày (BR-MAT-08)'),
  ('QUOTA_TUTOR', '30', 'Tin nhắn AI Tutor / ngày (BR-TUTOR-04)'),
  ('QUOTA_GUEST_SEARCH', '15', 'Tìm kiếm AI (khách) / giờ (BR-DISCOVERY-01)');
