package com.smart.report.fx;

import com.smart.report.ReportException;
import com.smart.report.pdf.Paper;
import com.smart.report.pdf.PdfBatch;
import com.smart.report.pdf.PdfDocument;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.smart.report.print.DocumentKind;
import com.smart.report.print.OutputMode;
import com.smart.report.print.PrintProfile;
import com.smart.report.print.PrintSettings;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.prefs.Preferences;

/**
 * نافذة معاينة لمستند PDF (أو دفعة مستندات، {@link #of(PdfBatch)}): صفحاته كما هتتطبع، واختيار الطابعة
 * والمقاس وعدد النسخ، وزرّا الطباعة وحفظ الملف.
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

    /** اللي بيتعاين: بايتات PDF على الورقة اللي المستخدم اختارها — مستند أو دفعة. */
    interface Source {
        byte[] pdf(Paper paper) throws ReportException;
    }

    final Source source;
    final List<Paper> papers = new ArrayList<>();
    final List<String> stylesheets = new ArrayList<>();
    Paper paper;
    String title;
    String fileName = "document.pdf";
    PreviewTexts texts;
    boolean rightToLeft;
    String rememberKey;
    /** الطابعة اللي النافذة بتفتح عليها لو موجودة؛ {@code null} = المحفوظة ثم الافتراضية. */
    String printer;
    int copies = 1;
    /** سطر فوق الصفحة من البرنامج؛ {@code null} = مفيش. */
    String notice;
    /** مستند أو دفعة جاهزين: الورقة بتاعتهم، ومقاس الإعداد ما بيغيرهاش. */
    boolean fixedPaper;
    /** الطابعات ونافذة الحفظ؛ الاختبارات بتبدّلها. */
    PreviewSystem system = PreviewSystem.REAL;

    private PdfPreview(Source source, boolean rightToLeft) {
        this.source = source;
        this.rightToLeft = rightToLeft;
    }

    /** معاينة مستند جاهز على ورقته؛ النافذة باتجاه المستند ولا تعرض اختيار مقاس. */
    public static PdfPreview of(PdfDocument document) {
        Objects.requireNonNull(document, "document");
        PdfPreview preview = new PdfPreview(paper -> document.toBytes(), document.rightToLeft());
        preview.papers.add(document.paper());
        preview.fixedPaper = true;
        return preview;
    }

    /**
     * معاينة دفعة جاهزة ({@link PdfBatch}) على ورقتها: كل مستنداتها ورا بعض زي ما هتتطبع، والطباعة مهمة
     * واحدة. النافذة باتجاه أول مستند ولا تعرض اختيار مقاس.
     */
    public static PdfPreview of(PdfBatch batch) {
        Objects.requireNonNull(batch, "batch");
        PdfPreview preview = new PdfPreview(paper -> batch.toBytes(), batch.documents().getFirst().rightToLeft());
        preview.papers.add(batch.paper());
        preview.fixedPaper = true;
        return preview;
    }

    /**
     * معاينة دفعة بتتبني لكل مقاس ورق، زي {@link #of(Function)}: كشوف كل السواقين، والمستخدم يختار A4 أو
     * A5 من النافذة. الدالة بتتنادى خارج thread الواجهة.
     */
    public static PdfPreview ofBatch(Function<Paper, PdfBatch> batch) {
        Objects.requireNonNull(batch, "batch");
        PdfPreview preview = new PdfPreview(paper -> batch.apply(paper).toBytes(),
                "ar".equals(Locale.getDefault().getLanguage()));
        preview.papers.add(Paper.A4);
        preview.papers.add(Paper.A5);
        return preview;
    }

    /**
     * معاينة مستند بيتبني لكل مقاس ورق: المستخدم بيغيّر المقاس من النافذة والمعاينة بتتبني من جديد.
     * المقاسات المعروضة A4 و A5 إلا لو {@link #papers(Paper...)} حدّد غيرها، والنافذة عربية لو لغة
     * الجهاز عربية إلا لو {@link #rightToLeft(boolean)} حدّد غير كده.
     */
    public static PdfPreview of(Function<Paper, PdfDocument> document) {
        Objects.requireNonNull(document, "document");
        PdfPreview preview = new PdfPreview(paper -> document.apply(paper).toBytes(),
                "ar".equals(Locale.getDefault().getLanguage()));
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

    /**
     * الاسم المقترح للملف عند «حفظ PDF». الحروف اللي ما تنفعش في اسم ملف على Windows ({@code \ / : * ? " < > |}
     * وحروف التحكم) بتبقى {@code -}، والنقط والمسافات في آخره بتتشال، واسم جهاز محجوز ({@code CON}،
     * {@code COM1}...) بياخد {@code _} قبله. فعنوان زي {@code "فاتورة 12/2026"} يتحفظ من غير ما البرنامج ينضفه.
     */
    public PdfPreview fileName(String name) {
        String safe = safeFileName(name);
        if (!safe.isEmpty()) {
            this.fileName = safe.toLowerCase(Locale.ROOT).endsWith(".pdf") ? safe : safe + ".pdf";
        }
        return this;
    }

    private static final java.util.regex.Pattern RESERVED =
            java.util.regex.Pattern.compile("(?i)(CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9])(\\..*)?");

    /** اسم ملف يقبله Windows ولينكس؛ فاضي لو ما فضلش فيه حاجة. */
    static String safeFileName(String name) {
        if (name == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(name.length());
        name.codePoints().forEach(c -> out.appendCodePoint(c < 0x20 || c == 0x7F || "\\/:*?\"<>|".indexOf(c) >= 0
                ? '-' : c));
        int end = out.length();
        while (end > 0 && (out.charAt(end - 1) == '.' || Character.isWhitespace(out.charAt(end - 1)))) {
            end--;
        }
        String safe = out.substring(0, end).strip();
        if (safe.chars().allMatch(c -> c == '-')) {
            return "";
        }
        return RESERVED.matcher(safe).matches() ? "_" + safe : safe;
    }

    /**
     * الطابعة اللي النافذة بتفتح عليها لو متعرّفة على الجهاز؛ من غيرها (أو لو مش موجودة) المحفوظة بـ
     * {@link #remember}، ثم الافتراضية.
     */
    public PdfPreview printer(String name) {
        this.printer = name == null || name.isBlank() ? null : name;
        return this;
    }

    /** عدد النسخ اللي النافذة بتفتح عليه، من 1 لـ 99. */
    public PdfPreview copies(int count) {
        if (count < 1 || count > PrintProfile.MAX_COPIES) {
            throw new IllegalArgumentException("Copies must be between 1 and " + PrintProfile.MAX_COPIES + ": " + count);
        }
        this.copies = count;
        return this;
    }

    /** سطر رسالة من البرنامج فوق الصفحة: «نسخة للعميل»، أو سبب فتح المعاينة؛ {@code null} أو فاضي = مفيش. */
    public PdfPreview notice(String text) {
        this.notice = text == null || text.isBlank() ? null : text;
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
                system.print(source.pdf(chosen), target, chosen, 1);
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

    /**
     * يطلّع المستند زي ما إعداد نوعه بيقول ({@link PrintSettings#profile}، مقري دلوقتي، فتغيير في شاشة الإعدادات
     * بيوصل لشاشة مفتوحة من قبله).
     *
     * @see #print(PrintSettings, String, UnaryOperator, Window)
     */
    public CompletableFuture<PrintResult> print(PrintSettings settings, String kind, Window owner) {
        return print(settings, kind, UnaryOperator.identity(), owner);
    }

    /**
     * يطلّع المستند زي ما إعداد نوعه بيقول، بعد تغيير لمرة واحدة ما بيتحفظش:
     * {@code profile -> profile.withMode(OutputMode.PREVIEW)} لزر «معاينة» جنب «طباعة»، أو
     * {@code profile -> profile.withPaper("A4")}.
     *
     * <ul>
     *   <li>{@link OutputMode#PRINT}: النسخ على طابعة الإعداد (الدور بيتحول لاسمها دلوقتي، ومن غير طابعة للدور
     *       الافتراضية)، والنتيجة {@link PrintResult.Printed}. لو الطابعة دي مش متعرّفة على الجهاز النافذة بتفتح
     *       وفوق الصفحة سطر بيقول كده، والنتيجة {@link PrintResult.Shown}: <b>المستند ما بيتبعتش لطابعة تانية
     *       من غير ما المستخدم يختار.</b></li>
     *   <li>{@link OutputMode#PREVIEW}: النافذة على ورقة الإعداد وطابعته ونسخه.</li>
     *   <li>{@link OutputMode#SAVE_PDF}: نافذة اختيار مكان الحفظ بالاسم من {@link #fileName}، والنتيجة
     *       {@link PrintResult.Saved} أو {@link PrintResult.Cancelled}.</li>
     *   <li>{@link OutputMode#ASK}: سؤال بتلات اختيارات، وبعدين زي اللي اتختار؛ القفل = {@link PrintResult.Cancelled}.</li>
     * </ul>
     *
     * <p>المستند اللي بيتبني لكل ورقة بيتبني على ورقة الإعداد، والنافذة بتعرض أوراق النوع؛ المستند الجاهز
     * ({@link #of(PdfDocument)}) بيفضل على ورقته. البناء والطباعة والكتابة خارج thread الواجهة، والنتيجة بتكمل
     * عليه. بتتنادى من thread الواجهة.</p>
     *
     * @throws IllegalArgumentException لو النوع مش معرّف، أو التغيير طلّع ورقة النوع ما بياخدهاش أو اسم ورقة
     *                                  ما يتقريش
     */
    public CompletableFuture<PrintResult> print(PrintSettings settings, String kind,
                                                UnaryOperator<PrintProfile> override, Window owner) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(override, "override");
        DocumentKind documentKind = settings.kind(kind);
        PrintProfile profile = Objects.requireNonNull(override.apply(settings.profile(kind)), "override result");
        documentKind.check(profile);
        Paper chosen = Paper.parse(profile.paper());
        if (!fixedPaper) {
            papers.clear();
            for (String name : documentKind.papers()) {
                try {
                    papers.add(Paper.parse(name));
                } catch (IllegalArgumentException unknown) {
                    log.warn("The paper {} of {} is not one Paper.parse reads; it is left out", name, kind);
                }
            }
            if (!papers.contains(chosen)) {
                papers.add(0, chosen);
            }
            paper = chosen;
        }
        Paper target = fixedPaper ? papers.get(0) : chosen;
        copies = profile.copies();
        Optional<String> printerName = settings.printerName(profile);
        printerName.ifPresent(name -> printer = name);
        PrintTexts words = PrintTexts.of(rightToLeft);

        OutputMode mode = profile.mode();
        if (mode == OutputMode.ASK) {
            mode = system.ask(owner, words);
            if (mode == null) {
                return CompletableFuture.completedFuture(new PrintResult.Cancelled());
            }
        }
        return switch (mode) {
            case PREVIEW, ASK -> CompletableFuture.completedFuture(new PrintResult.Shown(show(owner)));
            case SAVE_PDF -> saveTo(owner, target);
            case PRINT -> printTo(owner, printerName.orElse(null), target, profile.copies(), words);
        };
    }

    /** يكتب الملف في المكان اللي المستخدم اختاره، من غير نافذة معاينة. */
    private CompletableFuture<PrintResult> saveTo(Window owner, Paper target) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(resolvedTexts().saveTitle());
        chooser.setInitialFileName(fileName);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File file = system.chooseSaveFile(chooser, owner);
        if (file == null) {
            return CompletableFuture.completedFuture(new PrintResult.Cancelled());
        }
        CompletableFuture<PrintResult> result = new CompletableFuture<>();
        Thread thread = new Thread(() -> {
            PrintResult done;
            try {
                Files.write(file.toPath(), source.pdf(target));
                done = new PrintResult.Saved(file.toPath());
            } catch (ReportException | IOException | RuntimeException e) {
                log.warn("Saving the document to {} failed", file, e);
                done = new PrintResult.Failed(e);
            } catch (Error e) {
                Platform.runLater(() -> result.completeExceptionally(e));
                throw e;
            }
            PrintResult finished = done;
            Platform.runLater(() -> result.complete(finished));
        }, "pdf-preview-save");
        thread.setDaemon(true);
        thread.start();
        return result;
    }

    /**
     * يطبع على {@code name} (أو الافتراضية لو {@code null}) لو متعرّفة على الجهاز؛ غير كده يفتح النافذة بسطر
     * بيقول ليه، وما بيبعتش لطابعة تانية.
     */
    private CompletableFuture<PrintResult> printTo(Window owner, String name, Paper target, int count,
                                                   PrintTexts words) {
        CompletableFuture<PrintResult> result = new CompletableFuture<>();
        Thread thread = new Thread(() -> {
            String missing = null;
            PrintResult done = null;
            try {
                List<String> installed = system.printerNames();
                String chosen = name != null ? name : system.defaultPrinter().orElse(null);
                if (chosen == null || !installed.contains(chosen)) {
                    log.info("The profile's printer {} is not on this computer; opening the preview", chosen);
                    missing = chosen == null ? words.noPrinter() : String.format(words.missingPrinter(), chosen);
                } else {
                    try {
                        system.print(source.pdf(target), chosen, target, count);
                        done = new PrintResult.Printed(chosen, target, count);
                    } catch (ReportException | RuntimeException e) {
                        log.warn("Printing to {} failed", chosen, e);
                        done = new PrintResult.Failed(e);
                    }
                }
            } catch (RuntimeException e) {
                log.warn("Listing the printers failed", e);
                done = new PrintResult.Failed(e);
            } catch (Error e) {
                Platform.runLater(() -> result.completeExceptionally(e));
                throw e;
            }
            String why = missing;
            PrintResult finished = done;
            Platform.runLater(() -> {
                if (why == null) {
                    result.complete(finished);
                    return;
                }
                notice = why;
                try {
                    result.complete(new PrintResult.Shown(show(owner)));
                } catch (RuntimeException e) {
                    result.completeExceptionally(e);
                }
            });
        }, "pdf-preview-profile-print");
        thread.setDaemon(true);
        thread.start();
        return result;
    }
}
