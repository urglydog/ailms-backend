-- Hóa đơn / chứng từ (20/11/2026), giai đoạn H1: biên lai thanh toán (RCP), phiếu hoàn tiền (RFD), phiếu chi (PAY), biên lai công nợ (DBT).
-- Chứng từ BẤT BIẾN: không sửa, không xóa; sai thì hủy bằng phiếu điều chỉnh. Mỗi nguồn (loại + khóa nguồn) chỉ có đúng 1 chứng từ (uk_invoice_source).
-- source_id: id số của nguồn (khoản hoàn/yêu cầu rút/lần trả nợ) để so khớp bằng số, tránh so chuỗi khác collation; NULL với biên lai (khóa là mã đơn/nhóm đơn).
-- Không lưu file PDF — PDF sinh theo yêu cầu từ snapshot_json (ảnh chụp dữ liệu lúc phát hành). Migration này chỉ THÊM bảng/khóa cấu hình, không đụng dữ liệu cũ.
CREATE TABLE invoices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    number VARCHAR(30) NOT NULL,
    type VARCHAR(20) NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'ISSUED',
    party_user_id BIGINT NOT NULL,
    source_key VARCHAR(100) NOT NULL,
    source_id BIGINT NULL,
    related_invoice_id BIGINT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    subtotal DECIMAL(14,2) NOT NULL,
    discount DECIMAL(14,2) NOT NULL DEFAULT 0,
    total DECIMAL(14,2) NOT NULL,
    issued_at DATETIME NOT NULL,
    snapshot_json TEXT NOT NULL,
    template_version INT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL,
    UNIQUE KEY uk_invoice_number (number),
    UNIQUE KEY uk_invoice_source (type, source_key),
    UNIQUE KEY uk_invoice_source_id (type, source_id),
    INDEX idx_invoice_party (party_user_id, issued_at),
    INDEX idx_invoice_issued (issued_at),
    CONSTRAINT fk_invoice_party FOREIGN KEY (party_user_id) REFERENCES users(id),
    CONSTRAINT fk_invoice_related FOREIGN KEY (related_invoice_id) REFERENCES invoices(id)
);

CREATE TABLE invoice_lines (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    description VARCHAR(500) NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    unit_price DECIMAL(14,2) NOT NULL,
    amount DECIMAL(14,2) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL,
    UNIQUE KEY uk_invoice_line (invoice_id, line_no),
    CONSTRAINT fk_invoice_line_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id)
);

-- Bộ đếm số chứng từ theo loại + tháng (vd RCP-202611). Khóa dòng khi cấp số để không trùng/đứt quãng khi chạy song song.
CREATE TABLE invoice_sequences (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    seq_key VARCHAR(20) NOT NULL,
    seq_value BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL,
    UNIQUE KEY uk_invoice_seq_key (seq_key)
);

INSERT INTO system_configs (config_key, config_value, description) VALUES
  ('INVOICE_ISSUER_NAME', 'LinguaLearn', 'Tên bên phát hành hiển thị trên chứng từ'),
  ('INVOICE_ISSUER_NOTE', 'Graduation project - no tax code', 'Ghi chú bên phát hành trên chứng từ (chưa có mã số thuế)'),
  ('INVOICE_EXCLUDED_EMAIL_SUFFIX', '@example.com', 'Tài khoản có email kết thúc bằng đuôi này (dữ liệu demo) không được phát hành chứng từ; để trống nếu không loại trừ')
ON DUPLICATE KEY UPDATE config_key = config_key;
