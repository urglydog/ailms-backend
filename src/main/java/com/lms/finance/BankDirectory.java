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

    /** Tên gọi thường gặp → mã ngân hàng (người dùng hay gõ "MBBANK", "Vietcombank"...). */
    private static final java.util.Map<String, String> ALIASES = java.util.Map.ofEntries(
            java.util.Map.entry("MBBANK", "MB"), java.util.Map.entry("MBB", "MB"),
            java.util.Map.entry("TECHCOMBANK", "TCB"), java.util.Map.entry("VIETINBANK", "CTG"),
            java.util.Map.entry("VIETCOMBANK", "VCB"), java.util.Map.entry("SACOMBANK", "STB"),
            java.util.Map.entry("TPBANK", "TPB"), java.util.Map.entry("VPBANK", "VPB"),
            java.util.Map.entry("HDBANK", "HDB"), java.util.Map.entry("SEABANK", "SEAB"),
            java.util.Map.entry("EXIMBANK", "EIB"), java.util.Map.entry("LPBANK", "LPB"));

    private BankDirectory() {}

    private static String squash(String s) {
        return s == null ? "" : s.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    /**
     * Tìm ngân hàng từ chữ người dùng gõ (khớp CHÍNH XÁC sau khi bỏ khoảng trắng/ký tự lạ): theo mã, theo tên hoặc theo tên gọi quen thuộc.
     * Không khớp "gần đúng" có chủ đích — chi tiền tự động mà đoán nhầm ngân hàng là rủi ro, thà báo "không đọc được" để chuyển thủ công.
     */
    public static Optional<Bank> findByText(String text) {
        String key = squash(text);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        String aliased = ALIASES.getOrDefault(key, key);
        return BANKS.stream()
                .filter(b -> squash(b.code()).equals(aliased) || squash(b.name()).equals(key))
                .findFirst();
    }

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
