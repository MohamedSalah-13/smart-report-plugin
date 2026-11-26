package com.smart.report.pdf;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * قيمة من البيانات كنص في خلية تقرير الـ PDF العام.
 *
 * <p>{@code toString} كان بيطبع التاريخ {@code Thu Jan 01 02:00:00 EET 1970} والمبلغ {@code 1234567.5}
 * من غير فواصل. من غير نمط، الأرقام بفواصل الآلاف ({@code 1,234,567.5}) والتواريخ بنفس صيغ تقرير الـ
 * Excel ({@code 2026-11-26}، {@code 2026-11-26 10:30:00}، {@code 10:30:00}). الرقم ما بيتقرّبش:
 * {@code BigDecimal} بيفضل بكل خاناته ({@code 1,200.50})، والـ {@code double} بأقصر صورة بتمثّله
 * ({@code 7,200} مش {@code 7,200.0}).</p>
 *
 * <p><b>بنمط العمود:</b> الرقم بـ {@link DecimalFormat} (التقريب لأقرب، والنص 5 لفوق: {@code 2.345} ←
 * {@code 2.35})، والتاريخ بـ {@link DateTimeFormatter}. أسماء الشهور والأيام بلغة التقرير ({@code MMMM} ←
 * «نوفمبر» في التقرير العربي)، والأرقام والفواصل لاتينية دايمًا، فالتقرير واحد على أي جهاز.</p>
 *
 * <p>كائن لكل تقرير: الـ {@link DecimalFormat} مش آمن بين الـ threads، وكل نمط بيتجهّز مرة واحدة.</p>
 */
final class CellText {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);

    private final Locale locale;
    private final Map<Integer, DecimalFormat> byScale = new HashMap<>();
    private final Map<String, DecimalFormat> numberPatterns = new HashMap<>();
    private final Map<String, DateTimeFormatter> datePatterns = new HashMap<>();

    /** @param locale لغة أسماء الشهور والأيام في أنماط التواريخ */
    CellText(Locale locale) {
        this.locale = locale;
    }

    /** القيمة بالتنسيق الافتراضي. */
    String of(Object value) {
        if (value == null) {
            return "";
        }
        TemporalAccessor temporal = temporal(value);
        if (temporal != null) {
            DateTimeFormatter format = switch (temporal) {
                case LocalDate date -> DATE;
                case LocalTime time -> TIME;
                default -> DATE_TIME;
            };
            return format.format(temporal);
        }
        return switch (value) {
            case String text -> text;
            case BigDecimal decimal -> grouped(decimal);
            case BigInteger integer -> grouped(new BigDecimal(integer));
            case Double number -> number.isNaN() || number.isInfinite() ? number.toString()
                    : grouped(BigDecimal.valueOf(number).stripTrailingZeros());
            case Float number -> number.isNaN() || number.isInfinite() ? number.toString()
                    : grouped(new BigDecimal(number.toString()).stripTrailingZeros());
            case Byte number -> grouped(BigDecimal.valueOf(number));
            case Short number -> grouped(BigDecimal.valueOf(number));
            case Integer number -> grouped(BigDecimal.valueOf(number));
            case Long number -> grouped(BigDecimal.valueOf(number));
            case AtomicInteger number -> grouped(BigDecimal.valueOf(number.get()));
            case AtomicLong number -> grouped(BigDecimal.valueOf(number.get()));
            default -> String.valueOf(value);
        };
    }

    /**
     * القيمة بنمط عمودها: {@link DecimalFormat} للأرقام و{@link DateTimeFormatter} للتواريخ، وباقي الأنواع
     * بالتنسيق الافتراضي. {@code pattern} الفاضي = الافتراضي.
     *
     * @throws IllegalArgumentException لو النمط مش صالح، أو بيطلب حاجة مش في القيمة (ساعة من تاريخ بلا وقت)
     */
    String of(Object value, String pattern) {
        if (value == null || pattern == null || pattern.isBlank()) {
            return of(value);
        }
        TemporalAccessor temporal = temporal(value);
        try {
            if (temporal != null) {
                return datePatterns.computeIfAbsent(pattern, p -> DateTimeFormatter.ofPattern(p, locale))
                        .format(temporal);
            }
            if (value instanceof Number number) {
                return numberPatterns.computeIfAbsent(pattern, p -> {
                    DecimalFormat created = new DecimalFormat(p, DecimalFormatSymbols.getInstance(Locale.ROOT));
                    created.setRoundingMode(RoundingMode.HALF_UP);
                    return created;
                }).format(number);
            }
        } catch (RuntimeException e) { // DateTimeException والنمط المكسور
            throw new IllegalArgumentException("The pattern \"" + pattern + "\" cannot format the "
                    + value.getClass().getSimpleName() + " value " + value + ": " + e.getMessage(), e);
        }
        return of(value);
    }

    /**
     * القيمة كتاريخ/وقت يتنسّق، أو {@code null} لو مش تاريخ. اللحظات ({@code Date}، {@code Instant}...)
     * بتتحوّل لوقت منطقتها، فالنمط يقدر يطبع المنطقة نفسها كمان.
     */
    private static TemporalAccessor temporal(Object value) {
        return switch (value) {
            // أنواع java.sql بترث java.util.Date، وtoInstant() فيها بترمي: لازم تتفحص قبلها.
            case java.sql.Date date -> date.toLocalDate();
            case java.sql.Time time -> time.toLocalTime();
            case java.sql.Timestamp timestamp -> timestamp.toLocalDateTime();
            case Date date -> date.toInstant().atZone(ZoneId.systemDefault());
            case Calendar calendar -> calendar.toInstant().atZone(calendar.getTimeZone().toZoneId());
            case Instant instant -> instant.atZone(ZoneId.systemDefault());
            case LocalDate date -> date;
            case LocalDateTime dateTime -> dateTime;
            case LocalTime time -> time;
            case ZonedDateTime zoned -> zoned;
            case OffsetDateTime offset -> offset;
            default -> null;
        };
    }

    /** الرقم بفواصل الآلاف وبنفس خاناته العشرية بالظبط. */
    private String grouped(BigDecimal value) {
        int scale = Math.max(0, value.scale());
        DecimalFormat format = byScale.computeIfAbsent(scale, digits -> {
            DecimalFormat created = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.ROOT));
            created.setMinimumFractionDigits(digits);
            created.setMaximumFractionDigits(digits);
            return created;
        });
        return format.format(value);
    }
}
