package com.smart.report.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/** المستندات الجاهزة: الفاتورة بأشكالها، والإيصال، وكشف الحساب، والسند؛ بالعربي والإنجليزي. */
class ReadyDocumentsTest {

    private static final String LONG_NAME_AR = "مسمار حديد مجلفن برأس مسطح مقاس عشرة مليمتر علبة مائة قطعة";
    private static final String LONG_NAME_EN = "Galvanised flat head steel nail ten millimetres box of one hundred";

    static Invoice invoice(boolean arabic, int extraLines) {
        Invoice.Builder builder = Invoice.builder(arabic)
                .letterhead(arabic ? "مؤسسة النور للتجارة" : "Al Nour Trading", arabic ? "ت: 0100 000 0000" : "Tel 0100 000 0000")
                .title(arabic ? "فاتورة بيع" : "Sales invoice").number("1751").date("2026-11-28 10:30")
                .field(arabic ? "العميل" : "Customer", arabic ? "أحمد علي" : "Ahmed Ali")
                .field(arabic ? "الكاشير" : "Cashier", arabic ? "منى" : "Mona")
                .line(InvoiceLine.of(arabic ? LONG_NAME_AR : LONG_NAME_EN, "12", "2.50", "30.00")
                        .withCode("NL-10").withUnit(arabic ? "علبة" : "box"))
                .line(InvoiceLine.of(arabic ? "شاكوش" : "Hammer", "1", "85.00", "85.00").withCode("HM-1"));
        for (int i = 1; i <= extraLines; i++) {
            builder.line(InvoiceLine.of((arabic ? "صنف " : "Item ") + i, "1", "5.00", "5.00"));
        }
        return builder.total(arabic ? "الإجمالى" : "Subtotal", "115.00")
                .total(arabic ? "الضريبة" : "VAT", "16.10")
                .grandTotal(arabic ? "الصافى" : "Net", "131.10")
                .amountInWords(arabic ? "فقط مائة وواحد وثلاثون جنيهًا وعشرة قروش لا غير"
                        : "One hundred thirty-one Egyptian pounds and ten piastres only")
                .shopNote(arabic ? "البضاعة المباعة لا ترد بعد 14 يومًا" : "Goods are not returned after 14 days")
                .code("2100000017518")
                .build();
    }

    static AccountStatement statement(boolean arabic) {
        return AccountStatement.builder(arabic)
                .letterhead(arabic ? "مؤسسة النور للتجارة" : "Al Nour Trading")
                .title(arabic ? "كشف حساب عميل" : "Customer statement")
                .field(arabic ? "العميل" : "Customer", arabic ? "أحمد علي" : "Ahmed Ali")
                .field(arabic ? "الفترة" : "Period", "2026-11-01 : 2026-11-30")
                .figure(arabic ? "رصيد أول المدة" : "Opening balance", "1,000.00")
                .line("2026-11-02", arabic ? "فاتورة 1751" : "Invoice 1751", "2,500.00", "", "3,500.00")
                .line("2026-11-10", arabic ? "سند قبض 88" : "Receipt 88", "", "4,000.00", "-500.00")
                .closing(arabic ? "الرصيد الختامى" : "Closing balance", "-500.00")
                .build();
    }

    static Voucher voucher(boolean arabic) {
        return Voucher.builder(arabic)
                .letterhead(arabic ? "مؤسسة النور للتجارة" : "Al Nour Trading")
                .title(arabic ? "سند قبض" : "Receipt voucher").number("88").date("2026-11-10")
                .party(arabic ? "استلمنا من السيد" : "Received from", arabic ? "أحمد علي" : "Ahmed Ali")
                .amount(arabic ? "مبلغ وقدره" : "The sum of", "4,000.00")
                .amountInWords(arabic ? "فقط أربعة آلاف جنيه لا غير" : "Four thousand Egyptian pounds only")
                .purpose(arabic ? "وذلك عن" : "For", arabic ? "سداد فاتورة 1751" : "Payment of invoice 1751")
                .field(arabic ? "طريقة الدفع" : "Method", arabic ? "نقدًا" : "Cash")
                .signatures(arabic ? "المستلم" : "Received by", arabic ? "المحاسب" : "Accountant")
                .build();
    }

    private static Map<String, byte[]> documents() throws Exception {
        Map<String, byte[]> all = new LinkedHashMap<>();
        for (boolean arabic : new boolean[]{true, false}) {
            String lang = arabic ? "rtl" : "ltr";
            all.put("ready-invoice-a4-economy-" + lang, invoice(arabic, 0).toPdf(Paper.A4, InvoiceLayout.ECONOMY).toBytes());
            all.put("ready-invoice-a4-modern-" + lang, invoice(arabic, 0).toPdf(Paper.A4, InvoiceLayout.MODERN).toBytes());
            all.put("ready-invoice-a5-detailed-" + lang, invoice(arabic, 0).toPdf(Paper.A5, InvoiceLayout.DETAILED).toBytes());
            all.put("ready-receipt-80-" + lang, invoice(arabic, 0).toPdf(Paper.ROLL_80, InvoiceLayout.MODERN).toBytes());
            all.put("ready-receipt-58-" + lang, invoice(arabic, 0).toPdf(Paper.ROLL_58, InvoiceLayout.MODERN).toBytes());
            all.put("ready-statement-a4-" + lang, statement(arabic).toPdf(Paper.A4).toBytes());
            all.put("ready-statement-80-" + lang, statement(arabic).toPdf(Paper.ROLL_80).toBytes());
            all.put("ready-voucher-a5-" + lang, voucher(arabic).toPdf(Paper.A5).toBytes());
        }
        return all;
    }

    @TestFactory
    Stream<DynamicTest> eachDocumentIsDrawnAsRecorded() throws Exception {
        return documents().entrySet().stream().map(document -> dynamicTest(document.getKey(),
                () -> LayoutSnapshot.assertMatches(document.getKey(), LayoutSnapshot.full(document.getValue()))));
    }

    private static String text(byte[] pdf, int page) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            return stripper.getText(document);
        }
    }

    private static int pages(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return document.getNumberOfPages();
        }
    }

    /** فاتورة طويلة بتتقسم على صفحات، ورأس الجدول بيتكرر في كل صفحة، والملخص في الآخر. */
    @Test
    void aLongInvoiceBreaksWithItsHeaderRepeated() throws Exception {
        byte[] pdf = invoice(false, 120).toPdf(Paper.A5, InvoiceLayout.MODERN).toBytes();
        int count = pages(pdf);
        assertTrue(count >= 3, "pages: " + count);
        int withRows = 0;
        for (int page = 1; page <= count; page++) {
            String text = text(pdf, page);
            if (text.contains("Item ")) {
                withRows++;
                assertTrue(text.contains("Qty") && text.contains("Price"), "header on page " + page);
            }
        }
        assertTrue(withRows >= 3, "rows on " + withRows + " pages");
        assertTrue(text(pdf, count).contains("131.10"), "the net on the last page");
        assertFalse(text(pdf, 1).contains("131.10"));
    }

    /** الاسم الطويل في الإيصال بيلتف على أكتر من سطر بنفس الخط، مش بيصغر. */
    @Test
    void aLongNameWrapsWithoutShrinking() throws Exception {
        for (Paper roll : new Paper[]{Paper.ROLL_80, Paper.ROLL_58}) {
            Glyphs glyphs = Glyphs.of(invoice(false, 0).toPdf(roll, InvoiceLayout.MODERN).toBytes());
            Glyphs.Glyph first = glyphs.first("Galvanised");
            Glyphs.Glyph last = glyphs.first("hundred");
            assertTrue(last.y() > first.y() + 1, roll + ": the name takes more than one line");
            Glyphs.Glyph hammer = glyphs.first("Hammer");
            // نفس حجم الخط: عرض حرف H في Hammer قريب من عرضه لما يترسم في سطر الاسم الطويل بنفس الخط.
            float nameLetter = glyphs.first("Galvanised").width();
            float hammerLetter = hammer.width();
            assertEquals(hammerLetter, glyphs.first("Hammer").width(), 0.01f);
            assertTrue(nameLetter > 0.6f * hammerLetter, roll + ": the name is not shrunk");
            // الكمية × السعر تحت الاسم، والإجمالي جنبها.
            assertTrue(glyphs.first("12").y() > last.y(), roll + ": the detail line is under the name");
        }
        String receipt = text(invoice(true, 0).toPdf(Paper.ROLL_80, InvoiceLayout.MODERN).toBytes(), 1);
        assertTrue(receipt.contains("×"), receipt);
    }

    /** نفس الفاتورة على صفحة وعلى لفة. */
    @Test
    void oneInvoicePrintsEitherWay() throws Exception {
        Invoice saved = invoice(true, 0);
        assertEquals(1, pages(saved.toPdf(Paper.ROLL_80, InvoiceLayout.ECONOMY).toBytes()));
        assertEquals(1, pages(saved.toPdf(Paper.A4, InvoiceLayout.ECONOMY).toBytes()));
        try (PDDocument roll = Loader.loadPDF(saved.toPdf(Paper.ROLL_80, InvoiceLayout.ECONOMY).toBytes())) {
            assertEquals(Paper.ROLL_80.width(), roll.getPage(0).getMediaBox().getWidth(), 0.1f);
        }
    }

    @Test
    void theDetailedLayoutAddsLineNumbersCodesAndUnits() throws Exception {
        String detailed = text(invoice(false, 0).toPdf(Paper.A4, InvoiceLayout.DETAILED).toBytes(), 1);
        assertTrue(detailed.contains("NL-10") && detailed.contains("Code") && detailed.contains("#"), detailed);
        String modern = text(invoice(false, 0).toPdf(Paper.A4, InvoiceLayout.MODERN).toBytes(), 1);
        assertFalse(modern.contains("NL-10"), modern);
        assertTrue(modern.contains("box"), "units are shown whenever a line has one");
    }

    @Test
    void theStatementAndTheVoucher() throws Exception {
        String statement = text(statement(false).toPdf(Paper.A4).toBytes(), 1);
        for (String part : new String[]{"Customer statement", "Opening balance", "Invoice 1751", "3,500.00",
                "Closing balance"}) {
            assertTrue(statement.contains(part), part + " in " + statement);
        }
        String voucher = text(voucher(true).toPdf(Paper.A5).toBytes(), 1);
        for (String part : new String[]{"سند قبض", "أحمد علي", "4,000.00", "المحاسب"}) {
            assertTrue(voucher.contains(part), part + " in " + voucher);
        }
    }

    /** الرصيد الختامي السالب بالأحمر زي أرصدة الجدول؛ والموجب بلونه العادي. */
    @Test
    void aNegativeClosingBalanceIsRed() throws Exception {
        AccountStatement negative = AccountStatement.builder(false).title("Statement")
                .line("2026-11-02", "Invoice", "10.00", "", "10.00").closing("Closing balance", "-750.25").build();
        AccountStatement positive = AccountStatement.builder(false).title("Statement")
                .line("2026-11-02", "Invoice", "10.00", "", "10.00").closing("Closing balance", "750.25").build();
        try (PDDocument red = Loader.loadPDF(negative.toPdf(Paper.A4).toBytes());
             PDDocument plain = Loader.loadPDF(positive.toPdf(Paper.A4).toBytes())) {
            String redContent = new String(red.getPage(0).getContents().readAllBytes(),
                    java.nio.charset.StandardCharsets.ISO_8859_1);
            String plainContent = new String(plain.getPage(0).getContents().readAllBytes(),
                    java.nio.charset.StandardCharsets.ISO_8859_1);
            // CellStyle.RED = 0xC62828 = 0.77647 0.15686 0.15686
            assertTrue(redContent.contains("0.77647 0.15686 0.15686 rg"), "the negative closing is red");
            assertFalse(plainContent.contains("0.77647 0.15686 0.15686 rg"), "a positive closing is not");
        }
    }
}
