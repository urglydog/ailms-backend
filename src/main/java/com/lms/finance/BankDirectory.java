package com.lms.finance;

import java.util.List;
import java.util.Optional;

/**
 * Danh mục ngân hàng cho tài khoản nhận tiền (11/11/2026, Giai đoạn C). Mã {@code bin} theo chuẩn VietQR/NAPAS.
 * LƯU Ý: đợt này admin chuyển khoản tay theo TÊN ngân hàng + số tài khoản nên BIN chưa ảnh hưởng tới tiền; trước khi dùng
 * BIN cho chi trả tự động (PayOS payouts) hoặc tạo mã VietQR, phải đối chiếu lại với danh sách chính thức của VietQR.
 */
public final class BankDirectory {

    public record Bank(String code, String bin, String name) {}

    private static final List<Bank> BANKS = List.of(
            new Bank("VCB", "970436", "Vietcombank"),
            new Bank("CTG", "970415", "VietinBank"),
            new Bank("BIDV", "970418", "BIDV"),
            new Bank("AGRIBANK", "970405", "Agribank"),
            new Bank("MB", "970422", "MB Bank"),
            new Bank("TCB", "970407", "Techcombank"),
            new Bank("ACB", "970416", "ACB"),
            new Bank("VPB", "970432", "VPBank"),
            new Bank("TPB", "970423", "TPBank"),
            new Bank("STB", "970403", "Sacombank"),
            new Bank("HDB", "970437", "HDBank"),
            new Bank("VIB", "970441", "VIB"),
            new Bank("SHB", "970443", "SHB"),
            new Bank("MSB", "970426", "MSB"),
            new Bank("OCB", "970448", "OCB"),
            new Bank("SEAB", "970440", "SeABank"),
            new Bank("EIB", "970431", "Eximbank"),
            new Bank("LPB", "970449", "LPBank"),
            new Bank("NAB", "970428", "Nam A Bank"),
            new Bank("BAB", "970409", "Bac A Bank"),
            new Bank("PVCB", "970412", "PVcomBank"),
            new Bank("VBB", "970433", "VietBank"),
            new Bank("ABB", "970425", "ABBANK"),
            new Bank("NCB", "970419", "NCB"),
            new Bank("SCB", "970429", "SCB"),
            new Bank("KLB", "970452", "KienlongBank"),
            new Bank("BVB", "970438", "BaoViet Bank"),
            new Bank("VAB", "970427", "VietABank"));

    private BankDirectory() {}

    public static List<Bank> all() {
        return BANKS;
    }

    public static Optional<Bank> find(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return BANKS.stream().filter(b -> b.code().equalsIgnoreCase(code.trim())).findFirst();
    }
}
