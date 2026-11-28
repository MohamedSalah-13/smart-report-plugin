package com.smart.report.pdf;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

/**
 * الملفات المرفقة ({@link PdfDocument#attach}) و PDF/A-3B ({@link PdfDocument#pdfA3}): بيتحطوا على الـ PDF بعد ما
 * صفحاته تترسم وقبل ما يتكتب.
 */
final class PdfArchive {

    private static final String PRODUCER = "smart-report-pdf";
    private static final String SRGB = "sRGB IEC61966-2.1";

    private PdfArchive() {
    }

    static void apply(PDDocument pdf, PdfDocument document) throws IOException {
        List<PdfDocument.Attachment> files = document.attachments();
        if (files.isEmpty() && !document.pdfA3()) {
            return;
        }
        PDDocumentCatalog catalog = pdf.getDocumentCatalog();
        // تاريخ واحد بالثانية في القاموس وفي XMP: PDF/A بيطلبهم متطابقين.
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
        Calendar calendar = GregorianCalendar.from(now.toZonedDateTime());
        calendar.setTimeZone(TimeZone.getTimeZone("UTC"));
        PDDocumentInformation info = pdf.getDocumentInformation();
        info.setProducer(PRODUCER);
        info.setCreationDate(calendar);
        info.setModificationDate(calendar);

        if (!files.isEmpty()) {
            COSArray associated = new COSArray();
            Map<String, PDComplexFileSpecification> names = new TreeMap<>();
            for (PdfDocument.Attachment file : files) {
                PDEmbeddedFile embedded = new PDEmbeddedFile(pdf, new ByteArrayInputStream(file.data()));
                embedded.setSubtype(file.mimeType());
                embedded.setSize(file.data().length);
                embedded.setCreationDate(calendar);
                embedded.setModDate(calendar);
                PDComplexFileSpecification spec = new PDComplexFileSpecification();
                spec.setFile(file.name());
                spec.setFileUnicode(file.name());
                spec.setEmbeddedFile(embedded);
                spec.setEmbeddedFileUnicode(embedded);
                spec.setFileDescription(file.name());
                spec.getCOSObject().setName(COSName.getPDFName("AFRelationship"), file.relationship().pdfName());
                associated.add(spec);
                names.put(file.name(), spec);
            }
            PDEmbeddedFilesNameTreeNode tree = new PDEmbeddedFilesNameTreeNode();
            tree.setNames(names);
            PDDocumentNameDictionary dictionary = new PDDocumentNameDictionary(catalog);
            dictionary.setEmbeddedFiles(tree);
            catalog.setNames(dictionary);
            // الملفات «المرتبطة» بالمستند (PDF/A-3): الفاتورة الإلكترونية جزء من المستند مش مجرد مرفق.
            catalog.getCOSObject().setItem(COSName.getPDFName("AF"), associated);
        }

        if (document.pdfA3()) {
            PDOutputIntent intent = new PDOutputIntent(pdf,
                    new ByteArrayInputStream(ICC_Profile.getInstance(ColorSpace.CS_sRGB).getData()));
            intent.setInfo(SRGB);
            intent.setOutputCondition(SRGB);
            intent.setOutputConditionIdentifier(SRGB);
            intent.setRegistryName("http://www.color.org");
            catalog.addOutputIntent(intent);

            PDMetadata metadata = new PDMetadata(pdf);
            metadata.importXMPMetadata(xmp(info.getTitle(), info.getCreator(), now).getBytes(StandardCharsets.UTF_8));
            catalog.setMetadata(metadata);
            catalog.setLanguage(document.rightToLeft() ? "ar" : "en");
        }
    }

    /** XMP بنفس عنوان وأداة ومنتج وتواريخ قاموس المستند، ومعاها إعلان PDF/A-3B. */
    static String xmp(String title, String creator, OffsetDateTime when) {
        String date = when.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        StringBuilder out = new StringBuilder(1024);
        out.append("<?xpacket begin=\"﻿\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n")
                .append("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">\n")
                .append("<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n")
                .append("<rdf:Description rdf:about=\"\"")
                .append(" xmlns:pdfaid=\"http://www.aiim.org/pdfa/ns/id/\"")
                .append(" xmlns:dc=\"http://purl.org/dc/elements/1.1/\"")
                .append(" xmlns:xmp=\"http://ns.adobe.com/xap/1.0/\"")
                .append(" xmlns:pdf=\"http://ns.adobe.com/pdf/1.3/\">\n")
                .append("<pdfaid:part>3</pdfaid:part>\n")
                .append("<pdfaid:conformance>B</pdfaid:conformance>\n");
        if (title != null && !title.isEmpty()) {
            out.append("<dc:title><rdf:Alt><rdf:li xml:lang=\"x-default\">").append(escape(title))
                    .append("</rdf:li></rdf:Alt></dc:title>\n");
        }
        if (creator != null && !creator.isEmpty()) {
            out.append("<xmp:CreatorTool>").append(escape(creator)).append("</xmp:CreatorTool>\n");
        }
        out.append("<xmp:CreateDate>").append(date).append("</xmp:CreateDate>\n")
                .append("<xmp:ModifyDate>").append(date).append("</xmp:ModifyDate>\n")
                .append("<pdf:Producer>").append(PRODUCER).append("</pdf:Producer>\n")
                .append("</rdf:Description>\n</rdf:RDF>\n</x:xmpmeta>\n<?xpacket end=\"w\"?>");
        return out.toString();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
