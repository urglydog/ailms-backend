-- VNPay đã bị gỡ khỏi sản phẩm (chỉ còn PayOS). Chuyển các dòng payments cũ (gần như toàn bộ là
-- dữ liệu demo) sang PAYOS để giữ nguyên số tiền, ghi danh và doanh thu trên dashboard; không
-- xoá dòng nào và không đụng các dòng PayOS/MOMO/ZALOPAY sẵn có.
UPDATE payments SET payment_method = 'PAYOS' WHERE payment_method = 'VNPAY';
