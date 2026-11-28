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
 * A preview window for a PDF document (or a batch of documents, {@link #of(PdfBatch)}): its pages as they will
 * print, a choice of printer, paper size and number of copies, and the print and save buttons.
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
 * <p>The function that builds the document is called <b>off the UI thread</b>, once for each paper size the user
 * picks: do not touch any JavaFX node in it, and read your data before it. {@link #show(Window)} and
 * {@link #printOrShow(Window)} themselves are called from the UI thread.</p>
 */
public final class PdfPreview {

    private static final Logger log = LoggerFactory.getLogger(PdfPreview.class);

    /** Where the saved choices live in {@link Preferences}: under it, one node per {@link #remember} key. */
    static final String PREFERENCES = "com/smart/report/preview/";

    /** What is previewed: the PDF bytes on the paper the user picked, for a document or a batch. */
    interface Source {
        byte[] pdf(Paper paper) throws ReportException;
    }

    /**
     * PDF bytes built on the paper the user picked, from any source: a Jasper report, or another library. It is
     * called off the UI thread ({@link #ofPdf(PdfBytes)}).
     */
    @FunctionalInterface
    public interface PdfBytes {
        byte[] build(Paper paper) throws ReportException;
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
    /** The printer the window opens on, if it exists; {@code null} = the saved one, then the default. */
    String printer;
    int copies = 1;
    /** A line from the application above the page; {@code null} = none. */
    String notice;
    /** A ready document or batch: its own paper, which the profile's paper size does not change. */
    boolean fixedPaper;
    /** The printers and the save dialog; tests replace it. */
    PreviewSystem system = PreviewSystem.REAL;

    private PdfPreview(Source source, boolean rightToLeft) {
        this.source = source;
        this.rightToLeft = rightToLeft;
    }

    /** Previews a ready document on its paper; the window takes the document's direction and offers no size choice. */
    public static PdfPreview of(PdfDocument document) {
        Objects.requireNonNull(document, "document");
        PdfPreview preview = new PdfPreview(paper -> document.toBytes(), document.rightToLeft());
        preview.papers.add(document.paper());
        preview.fixedPaper = true;
        return preview;
    }

    /**
     * Previews a ready batch ({@link PdfBatch}) on its paper: all its documents one after another, as they will
     * print, and printing is a single job. The window takes the first document's direction and offers no size
     * choice.
     */
    public static PdfPreview of(PdfBatch batch) {
        Objects.requireNonNull(batch, "batch");
        PdfPreview preview = new PdfPreview(paper -> batch.toBytes(), batch.documents().getFirst().rightToLeft());
        preview.papers.add(batch.paper());
        preview.fixedPaper = true;
        return preview;
    }

    /**
     * Previews a batch that is built for each paper size, like {@link #of(Function)}: the statements of all
     * drivers, for example, with the user picking A4 or A5 in the window. The function is called off the UI thread.
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
     * Previews a document that is built for each paper size: the user changes the size in the window and the
     * preview is rebuilt. The sizes offered are A4 and A5 unless {@link #papers(Paper...)} sets others, and the
     * window is Arabic when the computer's language is Arabic, unless {@link #rightToLeft(boolean)} says otherwise.
     */
    public static PdfPreview of(Function<Paper, PdfDocument> document) {
        Objects.requireNonNull(document, "document");
        PdfPreview preview = new PdfPreview(paper -> document.apply(paper).toBytes(),
                "ar".equals(Locale.getDefault().getLanguage()));
        preview.papers.add(Paper.A4);
        preview.papers.add(Paper.A5);
        return preview;
    }

    /**
     * Previews a ready PDF from any source: a Jasper report ({@code ReportService.generate(ReportType.PDF, ...)} or
     * {@code JasperReportProvider.pdf(jasperPrint)}), or a file that was read. The paper comes from the first page's
     * size: A4 or A5 (portrait or landscape) if it is one of them, otherwise its size as it is. The window is Arabic
     * when the computer's language is Arabic, unless {@link #rightToLeft} says otherwise.
     *
     * @throws IllegalArgumentException if the bytes are not a readable PDF or it has no pages
     */
    public static PdfPreview ofPdf(byte[] pdf) {
        Objects.requireNonNull(pdf, "pdf");
        byte[] copy = pdf.clone();
        PdfPreview preview = new PdfPreview(paper -> copy, "ar".equals(Locale.getDefault().getLanguage()));
        preview.papers.add(paperOf(copy));
        preview.fixedPaper = true;
        return preview;
    }

    /**
     * Previews a PDF that is built for each paper the user picks, from any source (a Jasper report filled for the
     * size, for example). The sizes are A4 and A5 unless {@link #papers} sets others. The function is called off
     * the UI thread.
     */
    public static PdfPreview ofPdf(PdfBytes pdf) {
        Objects.requireNonNull(pdf, "pdf");
        PdfPreview preview = new PdfPreview(pdf::build, "ar".equals(Locale.getDefault().getLanguage()));
        preview.papers.add(Paper.A4);
        preview.papers.add(Paper.A5);
        return preview;
    }

    /** The first page's paper: A4 or A5, portrait or landscape, if within two points of it, otherwise its size. */
    static Paper paperOf(byte[] pdf) {
        try (com.smart.report.pdf.PdfPages pages = com.smart.report.pdf.PdfPages.open(pdf)) {
            if (pages.count() == 0) {
                throw new IllegalArgumentException("The PDF has no pages to preview.");
            }
            float width = pages.width(0);
            float height = pages.height(0);
            for (Paper known : new Paper[]{Paper.A4, Paper.A5, Paper.A4.landscape(), Paper.A5.landscape()}) {
                if (Math.abs(known.width() - width) < 2 && Math.abs(known.height() - height) < 2) {
                    return known;
                }
            }
            return new Paper("PDF", width, height);
        } catch (ReportException e) {
            throw new IllegalArgumentException("Not a PDF that can be read: " + e.getMessage(), e);
        }
    }

    /** The sizes the user can pick from; a single size = no choice. */
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

    /** The size the preview opens on; without it, the last saved size ({@link #remember}), then the first size. */
    public PdfPreview paper(Paper initial) {
        this.paper = Objects.requireNonNull(initial, "paper");
        return this;
    }

    /** The window title. */
    public PdfPreview title(String title) {
        this.title = title;
        return this;
    }

    /**
     * The suggested file name for «حفظ PDF» ("Save PDF"). Characters that are not allowed in a Windows file name
     * ({@code \ / : * ? " < > |} and control characters) become {@code -}, trailing periods and spaces are removed,
     * and a reserved device name ({@code CON}, {@code COM1}...) gets a {@code _} in front. So a title such as
     * {@code "فاتورة 12/2026"} is saved without the application having to clean it.
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

    /** A file name that Windows and Linux accept; empty when nothing is left of it. */
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
     * The printer the window opens on, if it is installed on the computer; without it (or if it is not there),
     * the one saved with {@link #remember}, then the default.
     */
    public PdfPreview printer(String name) {
        this.printer = name == null || name.isBlank() ? null : name;
        return this;
    }

    /** The number of copies the window opens with, from 1 to 99. */
    public PdfPreview copies(int count) {
        if (count < 1 || count > PrintProfile.MAX_COPIES) {
            throw new IllegalArgumentException("Copies must be between 1 and " + PrintProfile.MAX_COPIES + ": " + count);
        }
        this.copies = count;
        return this;
    }

    /**
     * A message line from the application above the page: «نسخة للعميل» ("Customer copy"), or why the preview
     * opened; {@code null} or empty = none.
     */
    public PdfPreview notice(String text) {
        this.notice = text == null || text.isBlank() ? null : text;
        return this;
    }

    /** The window direction, and the language of its texts (Arabic / English) when {@link #texts} is not set. */
    public PdfPreview rightToLeft(boolean value) {
        this.rightToLeft = value;
        return this;
    }

    /** The window texts, in the application's own terms. */
    public PdfPreview texts(PreviewTexts texts) {
        this.texts = Objects.requireNonNull(texts, "texts");
        return this;
    }

    /**
     * Saves the last printer and size the user picked, and opens on them next time. The key tells the
     * application's document types apart ("statement", "invoice"...): an office prints each type on the same
     * printer. With it, {@link #printOrShow(Window)} prints on the saved printer without a window.
     */
    public PdfPreview remember(String key) {
        this.rememberKey = key == null || key.isBlank() ? null : key;
        return this;
    }

    /** The application's CSS file, so the window's buttons match its look. */
    public PdfPreview stylesheet(String url) {
        if (url != null && !url.isBlank()) {
            stylesheets.add(url);
        }
        return this;
    }

    PreviewTexts resolvedTexts() {
        return texts != null ? texts : rightToLeft ? PreviewTexts.arabic() : PreviewTexts.english();
    }

    /** The saved-choices node for the {@link #remember} key, or {@code null} without one. */
    Preferences remembered() {
        return rememberKey == null ? null : Preferences.userRoot().node(PREFERENCES + rememberKey);
    }

    /** The size asked for explicitly, then the one saved last time, then the first size. */
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

    /** The name the size is saved under, which {@link Paper#parse} reads. */
    static String paperKey(Paper value) {
        return value.isLandscape() ? value.name() + "-landscape" : value.name();
    }

    /**
     * Opens the window over {@code owner} (or on its own when {@code null}) and returns it. The window does not
     * block the application: the user can leave it open and keep working.
     */
    public Stage show(Window owner) {
        return new PreviewWindow(this, owner).show();
    }

    /**
     * Prints straight away on the printer the user last printed on from the window; without one, opens the window
     * like {@link #show(Window)}. A cashier picks the printer once, and the receipts after that print without a
     * window.
     *
     * <ul>
     *   <li>Without {@link #remember(String)}, or when no printer is saved, or the saved one was removed from the
     *       computer: the window opens and the result is {@link PrintOutcome.Shown}. The printer the user prints on
     *       from it is saved for next time.</li>
     *   <li>Otherwise one copy is sent to the saved printer, on the size the window would have opened on
     *       ({@link #paper(Paper)}, then the last printed size, then the first size), and the result is
     *       {@link PrintOutcome.Sent}. That size is saved as the last printed size.</li>
     *   <li>If the document was not built or the printer rejected the job, the result is
     *       {@link PrintOutcome.Failed} and <b>the window does not open</b>: a window that pops up by itself during
     *       the next sale takes the keyboard from the cashier. The application tells the user, and can open the
     *       window itself with {@link #show(Window)}.</li>
     * </ul>
     *
     * <p>Called from the UI thread, and returns at once: listing the printers, building the document and printing
     * run on a background thread. The result completes on the UI thread, so whoever waits for it can touch the
     * application's nodes. It never completes exceptionally except for an unexpected error ({@link Error}).</p>
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
     * Outputs the document as its type's profile says ({@link PrintSettings#profile}, read now, so a change in the
     * settings screen reaches a screen that was opened before it).
     *
     * @see #print(PrintSettings, String, UnaryOperator, Window)
     */
    public CompletableFuture<PrintResult> print(PrintSettings settings, String kind, Window owner) {
        return print(settings, kind, UnaryOperator.identity(), owner);
    }

    /**
     * Outputs the document as its type's profile says, after a one-time change that is not saved:
     * {@code profile -> profile.withMode(OutputMode.PREVIEW)} for a «معاينة» ("Preview") button next to «طباعة»
     * ("Print"), or {@code profile -> profile.withPaper("A4")}.
     *
     * <ul>
     *   <li>{@link OutputMode#PRINT}: the copies go to the profile's printer (a role is resolved to its printer
     *       name now; a role with no printer means the default), and the result is {@link PrintResult.Printed}. If
     *       that printer is not installed on the computer, the window opens with a line above the page that says
     *       so, and the result is {@link PrintResult.Shown}: <b>the document is not sent to another printer without
     *       the user choosing.</b></li>
     *   <li>{@link OutputMode#PREVIEW}: the window on the profile's paper, printer and copies.</li>
     *   <li>{@link OutputMode#SAVE_PDF}: a dialog to choose where to save, with the name from {@link #fileName}, and
     *       the result is {@link PrintResult.Saved} or {@link PrintResult.Cancelled}.</li>
     *   <li>{@link OutputMode#ASK}: a question with three choices, then as chosen; closing it =
     *       {@link PrintResult.Cancelled}.</li>
     * </ul>
     *
     * <p>A document built for each paper is built on the profile's paper, and the window offers the type's papers;
     * a ready document ({@link #of(PdfDocument)}) stays on its own paper. Building, printing and writing run off
     * the UI thread, and the result completes on it. Called from the UI thread.</p>
     *
     * @throws IllegalArgumentException if the type is not defined, or the change produced a paper the type does
     *                                  not take or a paper name that cannot be read
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

    /** Writes the file where the user chose, without a preview window. */
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
     * Prints on {@code name} (or the default when {@code null}) if it is installed on the computer; otherwise opens
     * the window with a line that says why, and does not send to another printer.
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
