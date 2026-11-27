package com.smart.report.pdf;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.stream.Stream;

import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * مستندات تمثّل اللي المكتبة بتطبعه، مرسومة زي ما اتسجّلت من الكود قبل ما قواعد الصفحات تتجمع في مكان واحد:
 * نفس الصفحات، ونفس مكان كل كلمة وكل خط وكل خلفية ({@link LayoutSnapshot}). الاختبارات التانية بتقول إن
 * الجزء في الصفحة الصح ومكانه منطقي؛ ده بيقول إن مفيش حاجة اتحركت خالص.
 *
 * <p>المجموعات ({@link #nearThePageEnd()}) بتحط نفس الأجزاء على مسافات متدرجة من آخر الصفحة، عشان كل قرار
 * «يكفي هنا ولا صفحة جديدة» يتجرّب على الناحيتين.</p>
 */
class LayoutSnapshotTest {

    /** مستند بيتبني من جديد في كل مرة، عشان الاختبار ما يعتمدش على مستند اتكتب قبل كده. */
    @FunctionalInterface
    private interface Pdf {
        byte[] bytes() throws Exception;
    }

    @TestFactory
    Stream<DynamicTest> representativeDocumentsAreDrawnAsRecorded() {
        return documents().entrySet().stream().map(document -> dynamicTest(document.getKey(),
                () -> LayoutSnapshot.assertMatches(document.getKey(),
                        LayoutSnapshot.full(document.getValue().bytes()))));
    }

    @TestFactory
    Stream<DynamicTest> partsNearThePageEndLandWhereRecorded() {
        return nearThePageEnd().entrySet().stream().map(family -> dynamicTest(family.getKey(), () -> {
            StringBuilder all = new StringBuilder();
            for (Map.Entry<String, Pdf> document : family.getValue().entrySet()) {
                all.append("document ").append(document.getKey()).append('\n')
                        .append(LayoutSnapshot.lines(document.getValue().bytes()));
            }
            LayoutSnapshot.assertMatches(family.getKey(), all.toString());
        }));
    }

    // ===================== المستندات =====================

    private static Map<String, Pdf> documents() {
        Map<String, Pdf> documents = new LinkedHashMap<>();
        documents.put("a4-rtl-invoice", () -> invoice(PdfDocument.rtl(Paper.A4), true).toBytes());
        documents.put("a4-ltr-invoice", () -> invoice(PdfDocument.ltr(Paper.A4), false)
                .style(PdfStyle.forPaper(Paper.A4).withStripes(true)).toBytes());
        documents.put("a5-rtl-statement-letterhead-every-page", () -> statement(PdfDocument.rtl(Paper.A5), 40)
                .letterheadOnEveryPage(true).pageNumbers(PageNumbers.ALWAYS).pageFooter("صادر من نظام صفا للنقل")
                .toBytes());
        documents.put("a5-ltr-statement-ink-saver", () -> statement(PdfDocument.ltr(Paper.A5), 40)
                .style(PdfStyle.forPaper(Paper.A5).inkSaver().withGrid(false).withStripes(true))
                .heading("Fuel").table(t -> t.withoutHeader().column("", 2).column("", 1, Align.END)
                        .row("diesel", "1,200.00").row("oil", "150.00").totals("total", "1,350.00"))
                .footer("Printed by Naqlat").toBytes());
        documents.put("a4-landscape-custom-style", () -> customStyle().toBytes());
        documents.put("a5-ltr-parts-taller-than-a-page", () -> tallParts().toBytes());
        documents.put("a5-rtl-page-breaks", () -> pageBreaks().toBytes());
        documents.put("a5-rtl-empty-and-odd-parts", () -> oddParts().toBytes());
        documents.put("roll-80-rtl-receipt", () -> receipt(PdfDocument.rtl(Paper.ROLL_80), 6).toBytes());
        documents.put("roll-58-ltr-receipt-ending-in-a-heading", () -> PdfDocument.ltr(Paper.ROLL_58)
                .letterhead("Corner Shop", "Main St 12").title("Receipt").field("No", "0042")
                .table(items(4)).summaryTotal("Total", "40.00").qrCode("https://example.com/r/0042")
                .barcode("R-0042").heading("Notes").toBytes());
        documents.put("roll-80-ltr-receipt-longer-than-a-pdf-page", () -> PdfDocument.ltr(Paper.ROLL_80)
                .letterhead("Corner Shop").title("Receipt").space(Paper.MAX_PAGE_POINTS - 300).table(items(30))
                .summaryTotal("Total", "300.00").footer("Thank you").toBytes());
        documents.put("a5-batch", () -> PdfBatch.of(
                statement(PdfDocument.rtl(Paper.A5), 35).pageNumbers(PageNumbers.ALWAYS),
                PdfDocument.ltr(Paper.A5).letterhead("Beta Haulage").title("Short note").text("one line")
                        .signatures("Driver"),
                PdfDocument.rtl(Paper.A5).letterhead("Gamma").letterheadOnEveryPage(true).heading("one")
                        .text("first").pageBreak().heading("two").text("second").footer("end")).toBytes());
        return documents;
    }

    /** فاتورة فيها كل أنواع الأجزاء: ترويسة بشعار، بيانات، جدول، ملخص، تفقيط، توقيعات، QR، باركود. */
    private static PdfDocument invoice(PdfDocument document, boolean arabic) {
        if (arabic) {
            return document.logo(logo()).letterhead("شركة صفا للنقل", "القاهرة - 0100 000 0000", "س.ت 12345")
                    .title("فاتورة ضريبية مبسطة").subtitle("نوفمبر 2026")
                    .field("رقم الفاتورة", "INV-2026-0042").field("التاريخ", "2026-11-27")
                    .field("العميل", "مؤسسة النور للتجارة والمقاولات العامة والتوريدات الصناعية والزراعية")
                    .field("الرقم الضريبي", "300000000000003")
                    .heading("الأصناف")
                    .table(t -> t.column("الصنف", 4).column("الكمية", 1, Align.CENTER)
                            .column("السعر", 2, Align.END).column("الإجمالى", 2, Align.END)
                            .row("نقل بضائع من القاهرة إلى الإسكندرية بسيارة مبردة مع التحميل والتنزيل", "2",
                                    "1,500.00", "3,000.00")
                            .row("تحميل", "4", "250.00", "1,000.00")
                            .row("انتظار", "", "", "-")
                            .row("تأمين البضاعة", "1", "1,400.00", "1,400.00")
                            .totals("الإجمالى", "7", "", "5,400.00"))
                    .summary("المجموع", "5,400.00").summary("ضريبة القيمة المضافة 15%", "810.00")
                    .summaryTotal("المستحق", "6,210.00")
                    .text("فقط ستة آلاف ومائتان وعشرة جنيهات لا غير")
                    .signatures("المستلم", "المحاسب", "الختم")
                    .qrCode("https://example.com/invoice/INV-2026-0042")
                    .barcode("INV-2026-0042")
                    .footer("شكرا لتعاملكم معنا")
                    .pageFooter("هذا المستند صادر من نظام صفا");
        }
        return document.logo(logo()).letterhead("Safa Haulage", "Cairo - 0100 000 0000", "CR 12345")
                .title("Simplified tax invoice").subtitle("November 2026")
                .field("Invoice", "INV-2026-0042").field("Date", "2026-11-27")
                .field("Customer", "Al Nour trading, contracting, general supplies and industrial services company")
                .field("VAT number", "300000000000003")
                .heading("Items")
                .table(t -> t.column("Item", 4).column("Qty", 1, Align.CENTER).column("Price", 2, Align.END)
                        .column("Amount", 2, Align.END)
                        .row("Refrigerated freight from Cairo to Alexandria with loading and unloading", "2",
                                "1,500.00", "3,000.00")
                        .row("Loading", "4", "250.00", "1,000.00")
                        .row("Waiting", "", "", "-")
                        .row("Cargo insurance", "1", "1,400.00", "1,400.00")
                        .row("Pallets", "10", "0.00", "0.00")
                        .totals("Total", "17", "", "5,400.00"))
                .summary("Subtotal", "5,400.00").summary("VAT 15%", "810.00").summaryTotal("Due", "6,210.00")
                .text("Six thousand two hundred and ten pounds only", Align.END)
                .signatures("Received by", "Accountant", "Stamp")
                .qrCode("https://example.com/invoice/INV-2026-0042", Align.START)
                .barcode("INV-2026-0042", Align.END)
                .footer("Thank you for your business")
                .pageFooter("Issued by the Safa system");
    }

    /** كشف طويل: جدول على كذا صفحة برأسه في كل صفحة، وبعده الملخص والتفقيط والتوقيعات مع بعض. */
    private static PdfDocument statement(PdfDocument document, int rows) {
        boolean arabic = document.rightToLeft();
        PdfTable table = PdfTable.create().column(arabic ? "التاريخ" : "Date", 2)
                .column(arabic ? "البيان" : "Description", 4).column(arabic ? "القيمة" : "Amount", 2, Align.END);
        for (int i = 1; i <= rows; i++) {
            String what = i % 7 == 0
                    ? (arabic ? "نقل حمولة كاملة إلى المخزن الرئيسي في العاشر من رمضان" : "full load to the main"
                    + " warehouse in the tenth of Ramadan city")
                    : (arabic ? "نقلة " + i : "trip " + i);
            table.row("2026-11-" + (i % 30 + 1), what, i * 100 + ".00");
        }
        table.totals(arabic ? "الإجمالى" : "Total", "", "245,000.00");
        return document.letterhead(arabic ? "شركة صفا للنقل" : "Safa Haulage", "Cairo - 0100 000 0000")
                .title(arabic ? "كشف حساب عربية" : "Truck statement").subtitle(arabic ? "نوفمبر" : "November")
                .field(arabic ? "رقم العربية" : "Plate", "1234").field(arabic ? "السائق" : "Driver", "Ahmed")
                .heading(arabic ? "النقلات" : "Trips")
                .table(table)
                .summary(arabic ? "المستحق" : "Due", "245,000.00").summary(arabic ? "المدفوع" : "Paid", "200,000.00")
                .summaryTotal(arabic ? "الباقى" : "Remaining", "45,000.00")
                .space(4).text(arabic ? "فقط خمسة وأربعون ألف جنيه لا غير" : "Forty five thousand pounds only")
                .signatures(arabic ? "السائق" : "Driver", arabic ? "المحاسب" : "Accountant");
    }

    /** أعرض ورقة، وشكل مختلف في كل حاجة: هامش، ألوان، أحجام، ومحاذاة كل جزء. */
    private static PdfDocument customStyle() {
        PdfStyle style = PdfStyle.standard().scaled(1.1f).withMargin(50).withHeaderColors(0x1F4E79, 0xFFFFFF)
                .withTotalsColors(0xFCE4D6, 0x000000).withAccentColor(0xC00000).withStripes(true);
        return PdfDocument.ltr(Paper.A4.landscape()).style(style).letterhead("Wide Report Co.")
                .title("Quarterly summary").field("Quarter", "Q3").field("Region", "North").field("Owner", "Mona")
                .text("Centered note", Align.CENTER).text("Right note", Align.END)
                .table(t -> {
                    t.column("Month", 2).column("Trips", 1, Align.CENTER).column("Revenue", 2, Align.END)
                            .column("Notes", 5);
                    for (int i = 1; i <= 12; i++) {
                        t.row("M" + i, String.valueOf(i * 3), i * 1000 + ".00",
                                i % 4 == 0 ? "a longer note that wraps onto a second line in its column " + i : "");
                    }
                    t.totals("Total", "234", "78,000.00", "");
                })
                .qrCode("Q3-NORTH", 20, Align.END).barcode("Q3-2026", 8, Align.START)
                .pageNumbers(PageNumbers.ALWAYS);
    }

    /** أجزاء أطول من الصفحة: رأس جدول طويل بيترسم مرة، صف بيتقسم، بيان بيكمل، ومجموعة توقيع أطول من صفحة. */
    private static PdfDocument tallParts() {
        StringBuilder long1 = new StringBuilder();
        for (int word = 1; word <= 110; word++) {
            long1.append("w").append(word).append(' ');
        }
        StringBuilder header = new StringBuilder();
        for (int word = 1; word <= 40; word++) {
            header.append("head").append(word).append(' ');
        }
        StringBuilder note = new StringBuilder();
        for (int line = 1; line <= 45; line++) {
            note.append(line == 1 ? "" : "\n").append("note").append(line);
        }
        return PdfDocument.ltr(Paper.A5).letterhead("Safa Haulage").title("Tall parts")
                .table(t -> t.column(header.toString().trim(), 1).column("B", 3).row("a", "short")
                        .row("b", "short again"))
                .table(t -> t.column("Cell", 1).column("Other", 1).row("first", "x").row(long1.toString().trim(), "y")
                        .row("last", "z").totals("Total", "3"))
                .field("Long", long1.toString().trim()).field("Short", "1")
                .heading("Closing").table(t -> t.column("Day", 1).row("row1"))
                .summaryTotal("Remaining", "90").text(note.toString().trim()).signatures("AAA", "BBB");
    }

    /** أقسام بفواصل: بعد جدول طويل، فاصلين ورا بعض، فاصل في أول المستند، وفاصل في الآخر. */
    private static PdfDocument pageBreaks() {
        PdfDocument document = PdfDocument.rtl(Paper.A5).letterhead("شركة صفا للنقل", "القاهرة")
                .letterheadOnEveryPage(true).pageBreak().title("أقسام").footer("نهاية الكشف");
        for (int section = 1; section <= 3; section++) {
            PdfTable table = PdfTable.create().column("اليوم", 1).column("القيمة", 1, Align.END);
            for (int row = 1; row <= (section == 2 ? 50 : 6); row++) {
                table.row("row" + section + "-" + row, String.valueOf(row * 10));
            }
            document.heading("قسم " + section).table(table.totals("الإجمالى", "100")).pageBreak();
            if (section == 1) {
                document.pageBreak();
            }
        }
        return document.space(12).text("").pageBreak();
    }

    /** أجزاء فاضية أو غريبة: جداول من غير صفوف أو أعمدة أو رأس، عنوان فاضي، tab وسطر جديد في النص. */
    private static PdfDocument oddParts() {
        return PdfDocument.rtl(Paper.A5).title("").subtitle(null).heading(null).heading("   ")
                .field("", "").field("بيان", "").field("", "قيمة بلا بيان")
                .table(t -> t.column("فاضي", 1).column("كمان", 1))
                .table(PdfTable.create())
                .table(t -> t.withoutHeader().column("", 1).column("", 1).row("a", "b").totals("c", "d"))
                .space(0).text("سطر\tبعلامة\r\nوسطر تاني\n\nبعد سطر فاضي")
                .table(t -> t.column("Narrow", 1).column("Wide", 6)
                        .row("Supercalifragilisticexpialidocious-and-more-and-more", "x"))
                .text("").space(10).signatures("", "الختم")
                .pageNumbers(PageNumbers.NONE);
    }

    private static PdfTable items(int count) {
        PdfTable table = PdfTable.create().column("Item", 3).column("Qty", 1).column("Price", 2, Align.END);
        for (int i = 1; i <= count; i++) {
            table.row("item" + i, "1", "10.00");
        }
        return table;
    }

    /** إيصال حراري: بيتقاس الأول وبعدين بيترسم على ورقة بطوله. */
    private static PdfDocument receipt(PdfDocument document, int count) {
        PdfTable table = PdfTable.create().column("الصنف", 3).column("العدد", 1, Align.CENTER)
                .column("السعر", 2, Align.END);
        for (int i = 1; i <= count; i++) {
            table.row(i == 3 ? "صنف باسم طويل جدا بيلتف على سطرين في العمود" : "صنف " + i, "1", "10.00");
        }
        return document.letterhead("بقالة الناصية", "شارع النصر 12").title("إيصال").field("رقم", "0042")
                .field("الكاشير", "منى").table(table.totals("الإجمالى", String.valueOf(count), count * 10 + ".00"))
                .summary("المدفوع", "100.00").summaryTotal("الباقى", "40.00").text("أربعون جنيها")
                .signatures("المستلم").qrCode("https://example.com/r/0042").barcode("R-0042")
                .footer("شكرا لزيارتكم");
    }

    /** شعار صغير ثابت: PNG بيتعمل في الذاكرة بنفس البايتات في كل مرة. */
    private static byte[] logo() {
        BufferedImage image = new BufferedImage(120, 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(0x2C3E50));
        graphics.fillRect(0, 0, 120, 60);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(10, 10, 40, 40);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    // ===================== آخر الصفحة =====================

    /**
     * كل مجموعة: نفس الأجزاء بعد مسافة بتكبر شوية شوية، فبتقع في آخر الصفحة على كل الأوضاع — تكفي، تكفي بالعافية،
     * ما تكفيش وتتنقل، أو ما تكفيش وتتقسم.
     */
    private static Map<String, Map<String, Pdf>> nearThePageEnd() {
        Map<String, Map<String, Pdf>> families = new LinkedHashMap<>();
        families.put("near-end-closing-group", sweep(space -> {
            PdfDocument document = (space % 2 == 0 ? PdfDocument.ltr(Paper.A5) : PdfDocument.rtl(Paper.A5))
                    .letterhead("Safa Haulage").letterheadOnEveryPage(space % 4 < 2).space(space)
                    .summary("Due", "100").summary("Paid", "10").summaryTotal("Remaining", "90").space(4)
                    .text("Ninety only").signatures("AAA", "BBB").pageNumbers(PageNumbers.NONE);
            return document::toBytes;
        }, 300, 520, 3));
        families.put("near-end-text-and-signatures", sweep(space -> PdfDocument.ltr(Paper.A5)
                .letterhead("Safa Haulage").letterheadOnEveryPage(space % 2 == 0).space(space)
                .text("I received the goods listed above\nin good condition").space(6).signatures("AAA", "BBB")
                ::toBytes, 330, 520, 3));
        families.put("near-end-heading-and-table", sweep(space -> PdfDocument.rtl(Paper.A5).text("top").space(space)
                .heading("Section").table(t -> t.column("Day", 1).column("Amount", 1).row("r1", "1").row("r2", "2"))
                .text("after")::toBytes, 380, 540, 3));
        families.put("near-end-fields", sweep(space -> PdfDocument.ltr(Paper.A5).letterhead("Safa").space(space)
                .field("Address", "12 Nile street\nGarden City\nCairo").field("Phone", "0100")
                .field("Driver", "Ahmed").text("after")::toBytes, 400, 540, 3));
        families.put("near-end-qr-and-barcode", sweep(space -> PdfDocument.ltr(Paper.A5).text("top").space(space)
                .qrCode("https://example.com/q").barcode("B-0042").text("after")::toBytes, 300, 540, 6));
        families.put("near-end-rows", sweep(rows -> {
            PdfTable table = PdfTable.create().column("Day", 1).column("Amount", 1, Align.END);
            for (int i = 1; i <= rows; i++) {
                table.row(i % 9 == 0 ? "row" + i + "\nsecond line" : "row" + i, String.valueOf(i));
            }
            return PdfDocument.ltr(Paper.A5).letterhead("Safa").letterheadOnEveryPage(rows % 2 == 0)
                    .table(table.totals("Total", "-")).summary("Due", "100").summaryTotal("Remaining", "90")
                    .text("Ninety only").signatures("AAA", "BBB").pageNumbers(PageNumbers.NONE)::toBytes;
        }, 26, 44, 1));
        families.put("near-end-page-break", sweep(space -> PdfDocument.rtl(Paper.A5).letterhead("Safa")
                .letterheadOnEveryPage(space % 2 == 0).space(space).text("before").pageBreak().text("after")
                .footer("end")::toBytes, 440, 540, 5));
        families.put("roll-items", sweep(count -> PdfDocument.ltr(Paper.ROLL_80).letterhead("Corner Shop")
                .title("Receipt").table(items(count)).summary("Paid", "100.00").summaryTotal("Change", "40.00")
                .text("Forty only").signatures("Customer").heading("Notes")::toBytes, 1, 31, 5));
        return families;
    }

    private static Map<String, Pdf> sweep(IntFunction<Pdf> document, int from, int to, int step) {
        Map<String, Pdf> documents = new LinkedHashMap<>();
        for (int value = from; value <= to; value += step) {
            documents.put(String.valueOf(value), document.apply(value));
        }
        return documents;
    }
}
