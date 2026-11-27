package com.smart.report.pdf;

import com.smart.report.ReportException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * مستندات كتير في ملف PDF واحد ومهمة طباعة واحدة — كشف كل سواق عن الشهر، أو فواتير اليوم كلها.
 *
 * <pre>{@code
 * List<PdfDocument> statements = drivers.stream()
 *         .map(driver -> StatementPdf.document(driver, Paper.A4))
 *         .toList();
 * PdfBatch batch = PdfBatch.of(statements);
 *
 * batch.save(Path.of("statements-2026-11.pdf"));   // ملف واحد
 * PdfPrinter.print(batch, printerName, 2);          // مهمة واحدة: الكشوف كلها، وبعدها الكشوف كلها تاني
 * }</pre>
 *
 * <p><b>كل مستند بصفحاته زي ما لو اتطبع لوحده:</b> ترويسته، وتذييل صفحاته، وترقيمه («صفحة 1 من 3» بيبدأ
 * من الأول في كل مستند). المستند الجديد دايمًا في صفحة جديدة، والمستندات بترتيبها في الدفعة.</p>
 *
 * <p><b>الورقة:</b> مهمة الطباعة بتطلب ورقة واحدة من الطابعة، فكل المستندات لازم تتطبع على نفس الورقة:
 * نفس المقاس بالطول أو بالعرض (A4 مع A4 بالعرض ماشي — الصفحة العرضية بتتلف على الورقة)، أو نفس عرض
 * اللفة الحرارية. A4 مع A5، أو ورق مع لفة، أو لفة 80 مع لفة 58، بيفشل وقت كتابة الدفعة بـ
 * {@link ReportException}: اعمل دفعة لكل ورقة. على اللفة كل مستند إيصال لوحده، صفحة بطول المكتوب فيه،
 * فالطابعة اللي بتقص في آخر كل صفحة بتقص بين الإيصالات.</p>
 *
 * <p>الدفعة بتشيل المستندات نفسها مش نسخة منها: التعديل على مستند بعد {@link #of} بيظهر في اللي
 * بيتكتب بعده. زي {@link PdfDocument}، مش آمنة للاستخدام من أكتر من thread.</p>
 */
public final class PdfBatch {

    private final List<PdfDocument> documents;

    private PdfBatch(List<PdfDocument> documents) {
        this.documents = documents;
    }

    /** @throws IllegalArgumentException لو مفيش مستندات */
    public static PdfBatch of(PdfDocument... documents) {
        Objects.requireNonNull(documents, "documents");
        return of(Arrays.asList(documents));
    }

    /**
     * المستندات بترتيب الـ collection.
     *
     * @throws IllegalArgumentException لو مفيش مستندات
     */
    public static PdfBatch of(Collection<PdfDocument> documents) {
        Objects.requireNonNull(documents, "documents");
        List<PdfDocument> kept = new ArrayList<>(documents.size());
        for (PdfDocument document : documents) {
            kept.add(Objects.requireNonNull(document, "document " + (kept.size() + 1)));
        }
        if (kept.isEmpty()) {
            throw new IllegalArgumentException("A batch needs at least one document.");
        }
        return new PdfBatch(List.copyOf(kept));
    }

    /** المستندات بترتيبها. */
    public List<PdfDocument> documents() {
        return documents;
    }

    /** ورقة أول مستند: الورقة اللي الدفعة بتتطبع عليها، والباقيين لازم يتطبعوا على نفسها. */
    public Paper paper() {
        return documents.getFirst().paper();
    }

    /** الدفعة كبايتات PDF. */
    public byte[] toBytes() throws ReportException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeTo(out);
        return out.toByteArray();
    }

    /**
     * يكتب الدفعة في {@code out} ولا يقفله.
     *
     * @throws ReportException لو المستندات مش على نفس الورقة، أو مستند منهم ما اتبناش (الرسالة فيها رقمه في
     *                         الدفعة، والخطأ الأصلي سببها)
     */
    public void writeTo(OutputStream out) throws ReportException {
        Objects.requireNonNull(out, "out");
        checkOneSheet();
        // كل مستند بيتبني لوحده، فترقيمه وترويسته وطول إيصاله زي ما هم؛ وبعدين صفحاته بتتنسخ ورا بعض.
        // المستندات بتفضل مفتوحة لحد ما الملف يتكتب: الصفحات المنسوخة ممكن تقرا منها.
        List<PDDocument> parts = new ArrayList<>(documents.size());
        try (PDDocument merged = new PDDocument()) {
            PDFMergerUtility merger = new PDFMergerUtility();
            for (int i = 0; i < documents.size(); i++) {
                PDDocument part = Loader.loadPDF(build(i));
                parts.add(part);
                merger.appendDocument(merged, part);
            }
            // اسم مهمة الطباعة من عنوان الملف: عنوان أول مستند ليه عنوان.
            merged.getDocumentInformation().setCreator("smart-report-pdf");
            merged.getDocumentInformation().setTitle(parts.stream()
                    .map(part -> part.getDocumentInformation().getTitle())
                    .filter(title -> title != null && !title.isBlank())
                    .findFirst().orElse(null));
            merged.save(out);
        } catch (IOException e) {
            throw new ReportException("Failed to write the PDF batch: " + e.getMessage(), e);
        } finally {
            for (PDDocument part : parts) {
                try {
                    part.close();
                } catch (IOException ignored) {
                    // مستند في الذاكرة: مفيش ملف يفضل مفتوح.
                }
            }
        }
    }

    /**
     * المستند رقم {@code index} كبايتات. الخطأ بيقول رقمه في الدفعة (من 1) وبيشيل الخطأ الأصلي: في دفعة 40 كشف،
     * «الشعار مش صورة» من غير رقم ما بيقولش أنهي كشف. نفس النوع بيفضل — {@link ReportException} أو
     * {@link IllegalArgumentException} (هوامش ما بتسيبش مكان) — زي ما المستند لوحده بيرميه.
     */
    private byte[] build(int index) throws ReportException {
        String which = "Document " + (index + 1) + " of " + documents.size() + " in the batch";
        try {
            return documents.get(index).toBytes();
        } catch (ReportException e) {
            throw new ReportException(which + " could not be built: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(which + ": " + e.getMessage(), e);
        }
    }

    /** يحفظ الدفعة في ملف، وينشئ المجلدات الناقصة في مساره. */
    public void save(Path file) throws ReportException {
        Objects.requireNonNull(file, "file");
        byte[] bytes = toBytes();
        try {
            if (file.toAbsolutePath().getParent() != null) {
                Files.createDirectories(file.toAbsolutePath().getParent());
            }
            Files.write(file, bytes);
        } catch (IOException e) {
            throw new ReportException("Failed to write the PDF batch to " + file, e);
        }
    }

    /** كل المستندات على ورقة أول مستند: الطابعة بتتطلب منها ورقة واحدة للمهمة كلها. */
    private void checkOneSheet() throws ReportException {
        Paper first = paper();
        for (int i = 1; i < documents.size(); i++) {
            Paper other = documents.get(i).paper();
            if (!sameSheet(first, other)) {
                throw new ReportException("Every document in a batch must print on the same paper, but document "
                        + (i + 1) + " is on " + other.name() + " and the first is on " + first.name()
                        + ". A print job asks the printer for one paper: make one batch per paper.");
            }
        }
    }

    /** نفس الورقة في الطابعة: نفس المقاس بالطول أو بالعرض، أو لفتين بنفس العرض. */
    static boolean sameSheet(Paper a, Paper b) {
        if (a.roll() || b.roll()) {
            return a.roll() && b.roll() && Math.abs(a.width() - b.width()) < 1f;
        }
        Paper first = a.portrait();
        Paper second = b.portrait();
        return Math.abs(first.width() - second.width()) < 1f && Math.abs(first.height() - second.height()) < 1f;
    }
}
