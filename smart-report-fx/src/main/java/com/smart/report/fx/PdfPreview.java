package com.smart.report.fx;

import com.smart.report.pdf.Paper;
import com.smart.report.pdf.PdfDocument;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * نافذة معاينة لمستند PDF: صفحاته كما هتتطبع، واختيار الطابعة والمقاس وعدد النسخ، وزرّا الطباعة
 * وحفظ الملف.
 *
 * <pre>{@code
 * PdfPreview.of(paper -> StatementPdf.document(statement, companyName, paper))
 *         .papers(Paper.A4, Paper.A5)
 *         .title("كشف حساب عربية 1751")
 *         .fileName("كشف 1751.pdf")
 *         .rightToLeft(true)
 *         .remember("statement")
 *         .show(ownerWindow);
 * }</pre>
 *
 * <p>الدالة اللي بتبني المستند بتتنادى <b>خارج thread الواجهة</b>، مرة لكل مقاس ورق يختاره المستخدم:
 * ما تلمسش فيها أي عنصر JavaFX، واقرا بياناتك قبلها. {@link #show(Window)} نفسها بتتنادى من thread
 * الواجهة.</p>
 */
public final class PdfPreview {

    final Function<Paper, PdfDocument> source;
    final List<Paper> papers = new ArrayList<>();
    final List<String> stylesheets = new ArrayList<>();
    Paper paper;
    String title;
    String fileName = "document.pdf";
    PreviewTexts texts;
    boolean rightToLeft;
    String rememberKey;

    private PdfPreview(Function<Paper, PdfDocument> source, boolean rightToLeft) {
        this.source = source;
        this.rightToLeft = rightToLeft;
    }

    /** معاينة مستند جاهز على ورقته؛ النافذة باتجاه المستند ولا تعرض اختيار مقاس. */
    public static PdfPreview of(PdfDocument document) {
        Objects.requireNonNull(document, "document");
        PdfPreview preview = new PdfPreview(paper -> document, document.rightToLeft());
        preview.papers.add(document.paper());
        return preview;
    }

    /**
     * معاينة مستند بيتبني لكل مقاس ورق: المستخدم بيغيّر المقاس من النافذة والمعاينة بتتبني من جديد.
     * المقاسات المعروضة A4 و A5 إلا لو {@link #papers(Paper...)} حدّد غيرها، والنافذة عربية لو لغة
     * الجهاز عربية إلا لو {@link #rightToLeft(boolean)} حدّد غير كده.
     */
    public static PdfPreview of(Function<Paper, PdfDocument> document) {
        Objects.requireNonNull(document, "document");
        PdfPreview preview = new PdfPreview(document, "ar".equals(Locale.getDefault().getLanguage()));
        preview.papers.add(Paper.A4);
        preview.papers.add(Paper.A5);
        return preview;
    }

    /** المقاسات اللي المستخدم يختار منها؛ مقاس واحد = بلا اختيار. */
    public PdfPreview papers(Paper... choices) {
        if (choices == null || choices.length == 0) {
            throw new IllegalArgumentException("At least one paper is required.");
        }
        papers.clear();
        for (Paper choice : choices) {
            papers.add(Objects.requireNonNull(choice, "paper"));
        }
        return this;
    }

    /** المقاس اللي المعاينة بتفتح عليه؛ من غيره آخر مقاس اتحفظ ({@link #remember}) ثم أول المقاسات. */
    public PdfPreview paper(Paper initial) {
        this.paper = Objects.requireNonNull(initial, "paper");
        return this;
    }

    /** عنوان النافذة. */
    public PdfPreview title(String title) {
        this.title = title;
        return this;
    }

    /** الاسم المقترح للملف عند «حفظ PDF». */
    public PdfPreview fileName(String name) {
        if (name != null && !name.isBlank()) {
            this.fileName = name.toLowerCase(Locale.ROOT).endsWith(".pdf") ? name : name + ".pdf";
        }
        return this;
    }

    /** اتجاه النافذة ولغة كلامها (عربي / إنجليزي) لو {@link #texts} ما اتحددش. */
    public PdfPreview rightToLeft(boolean value) {
        this.rightToLeft = value;
        return this;
    }

    /** كلام النافذة بمصطلحات البرنامج. */
    public PdfPreview texts(PreviewTexts texts) {
        this.texts = Objects.requireNonNull(texts, "texts");
        return this;
    }

    /**
     * يحفظ آخر طابعة ومقاس اختارهم المستخدم ويفتح عليهم المرة الجاية. المفتاح بيفرّق بين أنواع
     * المستندات في البرنامج ("statement"، "invoice"...): المكتب بيطبع كل نوع على نفس الطابعة.
     */
    public PdfPreview remember(String key) {
        this.rememberKey = key == null || key.isBlank() ? null : key;
        return this;
    }

    /** ملف CSS بتاع البرنامج، عشان أزرار النافذة تبقى بشكله. */
    public PdfPreview stylesheet(String url) {
        if (url != null && !url.isBlank()) {
            stylesheets.add(url);
        }
        return this;
    }

    PreviewTexts resolvedTexts() {
        return texts != null ? texts : rightToLeft ? PreviewTexts.arabic() : PreviewTexts.english();
    }

    /**
     * يفتح النافذة فوق {@code owner} (أو مستقلة لو {@code null}) ويرجّعها. النافذة مش بتحجز
     * البرنامج: المستخدم يقدر يسيبها مفتوحة ويكمل شغله.
     */
    public Stage show(Window owner) {
        return new PreviewWindow(this, owner).show();
    }
}
