package com.lms.certificate.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.image.BufferedImage;
import java.util.Map;

/** BR-CERT-10 — QR code THẬT (không phải lưới ô vuông trang trí giả như bản demo trực quan),
 * encode đúng {@code verifyUrl} của chứng chỉ. */
public final class CertificateQrCode {

    private CertificateQrCode() {
    }

    public static BufferedImage generate(String content, int sizePx) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, 0
            );
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);
            BufferedImage image = new BufferedImage(sizePx, sizePx, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < sizePx; x++) {
                for (int y = 0; y < sizePx; y++) {
                    image.setRGB(x, y, matrix.get(x, y) ? 0x1B2A4A : 0xFBF8F0);
                }
            }
            return image;
        } catch (WriterException e) {
            throw new IllegalStateException("Khong sinh duoc QR code cho chung chi", e);
        }
    }
}
