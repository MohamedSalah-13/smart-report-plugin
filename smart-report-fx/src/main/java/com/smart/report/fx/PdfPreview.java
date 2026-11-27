package com.smart.report.fx;

import com.smart.report.ReportException;
import com.smart.report.pdf.Paper;
import com.smart.report.pdf.PdfDocument;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.prefs.Preferences;

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
 * ما تلمسش فيها أي عنصر JavaFX، واقرا بياناتك قبلها. {@link #show(Window)} و{@link #printOrShow(Window)}
 * نفسهم بيتنادوا من thread الواجهة.</p>
 */
public final class PdfPreview {

    private static final Logger log = LoggerFactory.getLogger(PdfPreview.class);

    /** مكان المحفوظ في {@link Preferences}: تحته عقدة لكل مفتاح {@link #remember}. */
    static final String PREFERENCES = "com/smart/report/preview/";

    final Function<Paper, PdfDocument> source;
    final List<Paper> papers = new ArrayList<>();
    final List<String> stylesheets = new ArrayList<>();
    Paper paper;
    String title;
    String fileName = "document.pdf";
    PreviewTexts texts;
    boolean rightToLeft;
    String rememberKey;
    /** الطابعات ونافذة الحفظ؛ الاختبارات بتبدّلها. */
    PreviewSystem system = PreviewSystem.REAL;

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
     * المستندات في البرنامج ("statement"، "invoice"...): المكتب بيطبع كل نوع على نفس الطابعة. ومعاه
     * {@link #printOrShow(Window)} بتطبع على الطابعة المحفوظة من غير نافذة.
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

    /** عقدة المحفوظ لمفتاح {@link #remember}، أو {@code null} من غيره. */
    Preferences remembered() {
        return rememberKey == null ? null : Preferences.userRoot().node(PREFERENCES + rememberKey);
    }

    /** المقاس المطلوب صراحة، ثم المحفوظ من آخر مرة، ثم أول المقاسات. */
    Paper startingPaper(Preferences remembered) {
        if (paper != null && papers.contains(paper)) {
            return paper;
        }
        String last = remembered == null ? null : remembered.get("paper", null);
        if (last != null) {
            try {
                Paper parsed = Paper.parse(last);
                if (papers.contains(parsed)) {
                    return parsed;
                }
            } catch (IllegalArgumentException unknown) {
                // مقاس محفوظ من نسخة تانية من البرنامج: بيتجاهل.
            }
        }
        return papers.get(0);
    }

    /** الاسم اللي المقاس بيتحفظ بيه، و{@link Paper#parse} بيقراه. */
    static String paperKey(Paper value) {
        return value.isLandscape() ? value.name() + "-landscape" : value.name();
    }

    /**
     * يفتح النافذة فوق {@code owner} (أو مستقلة لو {@code null}) ويرجّعها. النافذة مش بتحجز
     * البرنامج: المستخدم يقدر يسيبها مفتوحة ويكمل شغله.
     */
    public Stage show(Window owner) {
        return new PreviewWindow(this, owner).show();
    }

    /**
     * يطبع على طول على الطابعة اللي المستخدم طبع عليها آخر مرة من النافذة، ومن غيرها يفتح النافذة زي
     * {@link #show(Window)}: الكاشير يختار الطابعة مرة واحدة والإيصالات اللي بعدها تطلع من غير نافذة.
     *
     * <ul>
     *   <li>من غير {@link #remember(String)}، أو لو مفيش طابعة محفوظة، أو المحفوظة اتشالت من الجهاز:
     *       النافذة بتتفتح والنتيجة {@link PrintOutcome.Shown}. الطابعة اللي المستخدم يطبع عليها منها
     *       بتتحفظ للمرة الجاية.</li>
     *   <li>غير كده نسخة واحدة بتتبعت للطابعة المحفوظة على المقاس اللي النافذة كانت هتفتح عليه
     *       ({@link #paper(Paper)}، ثم آخر مقاس اتطبع، ثم أول المقاسات)، والنتيجة
     *       {@link PrintOutcome.Sent}. المقاس ده بيتحفظ كآخر مقاس اتطبع.</li>
     *   <li>لو المستند ما اتبناش أو الطابعة رفضت المهمة، النتيجة {@link PrintOutcome.Failed}
     *       و<b>النافذة ما بتتفتحش</b>: نافذة بتطلع لوحدها وسط البيعة اللي بعدها بتاخد الكيبورد من
     *       الكاشير. البرنامج يقول للمستخدم، ويقدر يفتح النافذة بنفسه بـ {@link #show(Window)}.</li>
     * </ul>
     *
     * <p>بتتنادى من thread الواجهة وبترجع على طول: سؤال الطابعات وبناء المستند والطباعة على thread
     * جانبي. النتيجة بتكمل على thread الواجهة، فاللي بيستناها يقدر يلمس عناصر البرنامج. ما بتكملش
     * باستثناء أبدًا إلا لخطأ مش متوقع ({@link Error}).</p>
     */
    public CompletableFuture<PrintOutcome> printOrShow(Window owner) {
        CompletableFuture<PrintOutcome> outcome = new CompletableFuture<>();
        Preferences remembered = remembered();
        String target = remembered == null ? null : remembered.get("printer", null);
        if (target == null) {
            outcome.complete(new PrintOutcome.Shown(show(owner)));
            return outcome;
        }
        Paper chosen = startingPaper(remembered);
        Thread thread = new Thread(() -> {
            PrintOutcome done;
            try {
                if (!system.printerNames().contains(target)) {
                    log.info("The remembered printer {} is not on this computer; opening the preview", target);
                    Platform.runLater(() -> {
                        try {
                            outcome.complete(new PrintOutcome.Shown(show(owner)));
                        } catch (RuntimeException e) {
                            outcome.completeExceptionally(e);
                        }
                    });
                    return;
                }
                system.print(source.apply(chosen).toBytes(), target, chosen, 1);
                done = new PrintOutcome.Sent(target, chosen);
            } catch (ReportException | RuntimeException e) {
                log.warn("Printing straight to {} failed", target, e);
                done = new PrintOutcome.Failed(target, chosen, e);
            } catch (Error e) {
                Platform.runLater(() -> outcome.completeExceptionally(e));
                throw e;
            }
            if (done instanceof PrintOutcome.Sent) {
                // آخر مقاس اتطبع، زي طباعة النافذة: paper(...) المختلف عن المحفوظ بيبقى هو المحفوظ.
                // بيتحفظ قبل ما النتيجة تكمل، فالنداء اللي بعدها بيلاقيه.
                try {
                    remembered.put("paper", paperKey(chosen));
                } catch (IllegalStateException removed) {
                    log.debug("The remembered settings for {} were removed while printing", rememberKey, removed);
                }
            }
            PrintOutcome result = done;
            Platform.runLater(() -> outcome.complete(result));
        }, "pdf-preview-direct-print");
        thread.setDaemon(true);
        thread.start();
        return outcome;
    }
}
