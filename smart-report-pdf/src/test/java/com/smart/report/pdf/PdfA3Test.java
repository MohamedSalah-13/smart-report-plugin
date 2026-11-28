package com.smart.report.pdf;

import com.smart.report.util.ZatcaQr;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;
import org.verapdf.pdfa.Foundries;
import org.verapdf.pdfa.PDFAParser;
import org.verapdf.pdfa.PDFAValidator;
import org.verapdf.pdfa.flavours.PDFAFlavour;
import org.verapdf.pdfa.results.TestAssertion;
import org.verapdf.pdfa.results.ValidationResult;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PDF/A-3B بملف XML مرفق، متراجع بـ veraPDF. */
class PdfA3Test {

    private static final byte[] XML = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\">"
            + "<ID>INV-1751</ID><Note>فاتورة ضريبية مبسطة</Note></Invoice>\n").getBytes(StandardCharsets.UTF_8);

    @BeforeAll
    static void veraPdf() {
        VeraGreenfieldFoundryProvider.initialise();
    }

    private static ValidationResult validate(byte[] pdf) throws Exception {
        try (PDFAParser parser = Foundries.defaultInstance().createParser(new ByteArrayInputStream(pdf),
                PDFAFlavour.PDFA_3_B);
             PDFAValidator validator = Foundries.defaultInstance().createValidator(PDFAFlavour.PDFA_3_B, false)) {
            return validator.validate(parser);
        }
    }

    private static String failures(ValidationResult result) {
        return result.getTestAssertions().stream()
                .filter(assertion -> assertion.getStatus() != TestAssertion.Status.PASSED)
                .map(assertion -> assertion.getRuleId().getClause() + "-" + assertion.getRuleId().getTestNumber()
                        + ": " + assertion.getMessage())
                .distinct().collect(Collectors.joining("\n"));
    }

    private static byte[] stamp() throws Exception {
        BufferedImage image = new BufferedImage(60, 60, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(20, 60, 200, 180));
        g.fillOval(0, 0, 60, 60);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /** فاتورة عربية فيها كل حاجة: ترويسة، جدول، ملخص، QR، باركود، ختم شفاف، علامة مائية، و XML مرفق. */
    private static PdfDocument invoice() throws Exception {
        String qr = ZatcaQr.payload("مؤسسة الهدى", "310122393500003", Instant.parse("2026-11-28T10:30:00Z"),
                new BigDecimal("1150.00"), new BigDecimal("150.00"));
        return PdfDocument.rtl(Paper.A4)
                .letterhead("مؤسسة الهدى للتجارة", "الرياض")
                .title("فاتورة ضريبية مبسطة 1751")
                .field("التاريخ", "2026-11-28")
                .table(t -> t.column("الصنف", 3).column("القيمة", 2, Align.END)
                        .row("مسمار", "1,000.00").totals("الإجمالى", "1,150.00"))
                .summaryTotal("الصافى", "1,150.00")
                .qrCode(qr)
                .barcode("INV-1751")
                .image(stamp(), 25, Align.END)
                .watermark("صورة")
                .attach("invoice.xml", XML, "application/xml")
                .pdfA3(true);
    }

    @Test
    void anArabicInvoiceWithItsXmlIsValidPdfA3b() throws Exception {
        ValidationResult result = validate(invoice().toBytes());
        assertTrue(result.isCompliant(), failures(result));
    }

    /** الفحص بيشتغل فعلًا: نفس المستند من غير {@code pdfA3} بيفشل. */
    @Test
    void theSameDocumentWithoutPdfA3Fails() throws Exception {
        ValidationResult result = validate(invoice().pdfA3(false).toBytes());
        assertTrue(!result.isCompliant(), "a plain PDF must not pass");
        assertTrue(failures(result).contains("6.6") || failures(result).contains("6.2"), failures(result));
    }

    @Test
    void aReceiptOnARollIsValidToo() throws Exception {
        byte[] pdf = PdfDocument.rtl(Paper.ROLL_80).title("إيصال").text("شكرا لزيارتكم").pdfA3(true).toBytes();
        ValidationResult result = validate(pdf);
        assertTrue(result.isCompliant(), failures(result));
    }

    /** الـ XML بيرجع بايت بايت، ومرتبط بالمستند بعلاقته. */
    @Test
    void theAttachedXmlReadsBackByteForByte() throws Exception {
        try (PDDocument document = Loader.loadPDF(invoice().toBytes())) {
            PDDocumentNameDictionary names = document.getDocumentCatalog().getNames();
            Map<String, PDComplexFileSpecification> files = names.getEmbeddedFiles().getNames();
            assertEquals(java.util.Set.of("invoice.xml"), files.keySet());
            PDComplexFileSpecification spec = files.get("invoice.xml");
            assertArrayEquals(XML, spec.getEmbeddedFile().toByteArray());
            assertEquals("application/xml", spec.getEmbeddedFile().getSubtype());
            assertEquals("Data", spec.getCOSObject().getNameAsString(COSName.getPDFName("AFRelationship")));
            COSArray associated = (COSArray) document.getDocumentCatalog().getCOSObject()
                    .getDictionaryObject(COSName.getPDFName("AF"));
            assertEquals(1, associated.size());
            assertEquals(spec.getCOSObject(), (COSDictionary) associated.getObject(0));
        }
    }

    /** المرفق من غير PDF/A: ملف مرفق عادي، من غير ملف ألوان ولا XMP. */
    @Test
    void anAttachmentWithoutPdfA() throws Exception {
        byte[] pdf = PdfDocument.ltr(Paper.A4).title("Invoice")
                .attach("a.xml", XML, "text/xml", PdfDocument.Relationship.ALTERNATIVE)
                .attach("b.csv", "x,y".getBytes(StandardCharsets.UTF_8), "text/csv", PdfDocument.Relationship.SUPPLEMENT)
                .toBytes();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            Map<String, PDComplexFileSpecification> files =
                    document.getDocumentCatalog().getNames().getEmbeddedFiles().getNames();
            assertEquals(java.util.Set.of("a.xml", "b.csv"), files.keySet());
            assertEquals("Supplement",
                    files.get("b.csv").getCOSObject().getNameAsString(COSName.getPDFName("AFRelationship")));
            assertTrue(document.getDocumentCatalog().getOutputIntents().isEmpty());
            assertNull(document.getDocumentCatalog().getMetadata());
        }
    }

    @Test
    void withoutEitherNothingIsAdded() throws Exception {
        try (PDDocument document = Loader.loadPDF(PdfDocument.ltr(Paper.A4).title("x").toBytes())) {
            assertNull(document.getDocumentCatalog().getNames());
            assertNull(document.getDocumentCatalog().getMetadata());
        }
    }

    @Test
    void badAttachmentsAreRefused() {
        PdfDocument document = PdfDocument.ltr(Paper.A4).attach("invoice.xml", XML, "application/xml");
        assertThrows(IllegalArgumentException.class, () -> document.attach("invoice.xml", XML, "application/xml"));
        assertThrows(IllegalArgumentException.class, () -> document.attach(" ", XML, "application/xml"));
        assertThrows(IllegalArgumentException.class, () -> document.attach("a/b.xml", XML, "application/xml"));
        assertThrows(IllegalArgumentException.class, () -> document.attach("c.xml", XML, "xml"));
        assertThrows(NullPointerException.class, () -> document.attach("c.xml", null, "application/xml"));
    }

    @Test
    void theXmpEscapesTheTitle() {
        String xmp = PdfArchive.xmp("A & B <1>", "smart-report-pdf", java.time.OffsetDateTime.parse(
                "2026-11-28T10:30:00Z"));
        assertTrue(xmp.contains("A &amp; B &lt;1&gt;"), xmp);
        assertTrue(xmp.contains("<pdfaid:part>3</pdfaid:part>"));
        assertTrue(xmp.contains("<xmp:CreateDate>2026-11-28T10:30:00Z</xmp:CreateDate>"), xmp);
    }
}
