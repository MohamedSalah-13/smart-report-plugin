package com.smart.report.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * المستند مكتوبًا نصًا: كل صفحة بمقاسها، وكل كلمة مرسومة بمكانها، وكل أمر رسم في الصفحة (خط، خلفية، رمز،
 * صورة، ومكان كل نص ولونه وخطه). الملف المسجّل بيتقارن بالمرسوم دلوقتي رقمًا رقمًا بفرق 0.01 نقطة، فتغيير
 * ترتيب حساب في الكود ما بيفشلش، وأي جزء اتنقل أو اتغيّر شكله بيفشل بالسطر اللي اتغير.
 *
 * <p>تحديث الملفات المسجّلة بعد تغيير مقصود في الشكل: {@code ./mvnw -pl smart-report-pdf test
 * -Dtest=LayoutSnapshotTest -Dlayout.snapshots.update=true}، وبعدين راجع الفرق في git.</p>
 */
final class LayoutSnapshot {

    /** الفرق المسموح بين رقمين بالنقطة: أقل بكتير من أي تغيير يتشاف، وأكبر من فرق التقريب. */
    private static final double TOLERANCE = 0.01;

    private static final boolean UPDATE = Boolean.getBoolean("layout.snapshots.update");

    private LayoutSnapshot() {
    }

    /** كل حاجة في المستند: الكلمات بمواضعها وأوامر الرسم. */
    static String full(byte[] pdf) {
        return dump(pdf, true);
    }

    /** سطور كل صفحة بمكانها ونصها: الأخف، للمستندات الكتير اللي بتختبر أماكن نهاية الصفحة. */
    static String lines(byte[] pdf) {
        return dump(pdf, false);
    }

    private static String dump(byte[] bytes, boolean full) {
        StringBuilder out = new StringBuilder();
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            String title = pdf.getDocumentInformation().getTitle();
            out.append("title ").append(title == null ? "-" : quote(title)).append('\n');
            out.append("pages ").append(pdf.getNumberOfPages()).append('\n');
            List<List<TextPosition>> glyphs = glyphs(pdf);
            for (int i = 0; i < pdf.getNumberOfPages(); i++) {
                PDPage page = pdf.getPage(i);
                PDRectangle box = page.getMediaBox();
                out.append("page ").append(i + 1).append(' ').append(number(box.getWidth())).append(' ')
                        .append(number(box.getHeight())).append('\n');
                if (full) {
                    words(glyphs.get(i), out);
                    operators(page, out);
                } else {
                    textLines(glyphs.get(i), out);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toString();
    }

    /** الحروف المرسومة في كل صفحة بترتيب رسمها. */
    private static List<List<TextPosition>> glyphs(PDDocument pdf) throws IOException {
        List<List<TextPosition>> pages = new ArrayList<>();
        for (int i = 0; i < pdf.getNumberOfPages(); i++) {
            pages.add(new ArrayList<>());
        }
        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void processTextPosition(TextPosition text) {
                pages.get(getCurrentPageNo() - 1).add(text);
                super.processTextPosition(text);
            }
        };
        stripper.getText(pdf);
        return pages;
    }

    /** الحروف المتلاصقة على نفس السطر كلمة واحدة: مكان أولها من أعلى يسار الصفحة، وحافة آخرها. */
    private static void words(List<TextPosition> glyphs, StringBuilder out) {
        StringBuilder word = new StringBuilder();
        float x = 0;
        float y = 0;
        float right = 0;
        for (TextPosition glyph : glyphs) {
            String text = glyph.getUnicode();
            boolean blank = text == null || text.isBlank();
            boolean joins = !word.isEmpty() && Math.abs(glyph.getYDirAdj() - y) < 0.01f
                    && Math.abs(glyph.getXDirAdj() - right) < 0.05f;
            if (!word.isEmpty() && (blank || !joins)) {
                word(out, x, y, right, word);
                word.setLength(0);
            }
            if (blank) {
                continue;
            }
            if (word.isEmpty()) {
                x = glyph.getXDirAdj();
                y = glyph.getYDirAdj();
            }
            word.append(text);
            right = glyph.getXDirAdj() + glyph.getWidthDirAdj();
        }
        if (!word.isEmpty()) {
            word(out, x, y, right, word);
        }
    }

    private static void word(StringBuilder out, float x, float y, float right, CharSequence text) {
        out.append("  text ").append(number(x)).append(' ').append(number(y)).append(' ').append(number(right))
                .append(' ').append(quote(text.toString())).append('\n');
    }

    /** سطور الصفحة من فوق لتحت: مكان السطر وأول حرف فيه من الشمال، ونصه بمسافة مكان كل فراغ. */
    private static void textLines(List<TextPosition> glyphs, StringBuilder out) {
        List<TextPosition> sorted = new ArrayList<>(glyphs.stream()
                .filter(glyph -> glyph.getUnicode() != null && !glyph.getUnicode().isBlank()).toList());
        sorted.sort(Comparator.comparingDouble(TextPosition::getYDirAdj));
        int start = 0;
        while (start < sorted.size()) {
            float y = sorted.get(start).getYDirAdj();
            int end = start;
            while (end < sorted.size() && sorted.get(end).getYDirAdj() - y <= 0.5f) {
                end++;
            }
            List<TextPosition> line = new ArrayList<>(sorted.subList(start, end));
            line.sort(Comparator.comparingDouble(TextPosition::getXDirAdj));
            StringBuilder text = new StringBuilder();
            float right = Float.NaN;
            for (TextPosition glyph : line) {
                if (!Float.isNaN(right) && glyph.getXDirAdj() - right > 1f) {
                    text.append(' ');
                }
                text.append(glyph.getUnicode());
                right = glyph.getXDirAdj() + glyph.getWidthDirAdj();
            }
            out.append("  line ").append(number(y)).append(' ').append(number(line.getFirst().getXDirAdj()))
                    .append(' ').append(quote(text.toString())).append('\n');
            start = end;
        }
    }

    /** أوامر الرسم في الصفحة بالترتيب؛ كل نص (BT ... ET) في سطر واحد، والحروف نفسها بأرقامها في الخط. */
    private static void operators(PDPage page, StringBuilder out) throws IOException {
        PDFStreamParser parser = new PDFStreamParser(page);
        List<String> operands = new ArrayList<>();
        StringBuilder text = null;
        for (Object token = parser.parseNextToken(); token != null; token = parser.parseNextToken()) {
            if (!(token instanceof Operator operator)) {
                operands.add(operand((COSBase) token));
                continue;
            }
            StringBuilder op = new StringBuilder();
            for (String operand : operands) {
                op.append(operand).append(' ');
            }
            op.append(operator.getName());
            operands.clear();
            if (operator.getName().equals("BT")) {
                text = new StringBuilder("  op BT");
            } else if (text != null) {
                text.append(' ').append(op);
                if (operator.getName().equals("ET")) {
                    out.append(text).append('\n');
                    text = null;
                }
            } else {
                out.append("  op ").append(op).append('\n');
            }
        }
    }

    private static String operand(COSBase value) {
        return switch (value) {
            case COSNumber number -> number(number.floatValue());
            case COSName name -> "/" + name.getName();
            case COSString string -> "<" + HexFormat.of().formatHex(string.getBytes()) + ">";
            case COSArray array -> {
                StringBuilder out = new StringBuilder("[");
                for (int i = 0; i < array.size(); i++) {
                    out.append(i == 0 ? "" : " ").append(operand(array.get(i)));
                }
                yield out.append(']').toString();
            }
            default -> String.valueOf(value);
        };
    }

    private static String number(float value) {
        String text = String.format(Locale.ROOT, "%.3f", value);
        return text.equals("-0.000") ? "0.000" : text;
    }

    private static String quote(String text) {
        return '"' + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + '"';
    }

    /**
     * المرسوم دلوقتي زي الملف المسجّل {@code layout/<name>.txt}. لو مختلف، المرسوم بيتكتب في
     * {@code target/layout-snapshots/<name>.txt} عشان يتقارن بالمسجّل، والفشل بيقول أول سطر اختلف.
     */
    static void assertMatches(String name, String actual) {
        Path recorded = Path.of("src", "test", "resources", "layout", name + ".txt");
        if (UPDATE) {
            write(recorded, actual);
            return;
        }
        String expected;
        try (InputStream in = LayoutSnapshot.class.getResourceAsStream("/layout/" + name + ".txt")) {
            if (in == null) {
                throw new AssertionError("No recorded layout for \"" + name + "\": run once with"
                        + " -Dlayout.snapshots.update=true to record it.");
            }
            expected = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String difference = difference(expected, actual);
        if (difference != null) {
            Path drawn = Path.of("target", "layout-snapshots", name + ".txt");
            write(drawn, actual);
            throw new AssertionError(name + " is not drawn as recorded in " + recorded + " (drawn now: " + drawn
                    + ").\n" + difference);
        }
    }

    /** أول سطر مختلف بفرق أكبر من {@link #TOLERANCE} في رقم أو أي فرق في غير الأرقام، أو {@code null}. */
    private static String difference(String expected, String actual) {
        String[] want = expected.split("\n", -1);
        String[] got = actual.split("\n", -1);
        for (int i = 0; i < Math.max(want.length, got.length); i++) {
            String a = i < want.length ? want[i] : "<nothing>";
            String b = i < got.length ? got[i] : "<nothing>";
            if (!sameLine(a, b)) {
                return "line " + (i + 1) + "\n  recorded: " + a + "\n  drawn:    " + b;
            }
        }
        return null;
    }

    private static boolean sameLine(String expected, String actual) {
        if (expected.equals(actual)) {
            return true;
        }
        String[] want = tokens(expected);
        String[] got = tokens(actual);
        if (want.length != got.length) {
            return false;
        }
        for (int i = 0; i < want.length; i++) {
            if (want[i].equals(got[i])) {
                continue;
            }
            Double a = numeric(want[i]);
            Double b = numeric(got[i]);
            if (a == null || b == null || Math.abs(a - b) > TOLERANCE) {
                return false;
            }
        }
        return true;
    }

    /** الكلمات اللي بين علامتي تنصيص (آخر السطر) كلمة واحدة، والباقي بالمسافات، والأقواس لوحدها. */
    private static String[] tokens(String line) {
        int quote = line.indexOf('"');
        String head = quote < 0 ? line : line.substring(0, quote);
        List<String> tokens = new ArrayList<>(List.of(head.replace("[", " [ ").replace("]", " ] ").trim()
                .split(" +")));
        if (quote >= 0) {
            tokens.add(line.substring(quote));
        }
        return tokens.toArray(String[]::new);
    }

    private static Double numeric(String token) {
        if (!token.matches("-?\\d+\\.\\d+")) {
            return null;
        }
        return Double.parseDouble(token);
    }

    private static void write(Path file, String text) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
