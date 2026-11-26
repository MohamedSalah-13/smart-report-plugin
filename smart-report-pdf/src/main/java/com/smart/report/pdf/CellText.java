package com.smart.report.pdf;

import java.math.BigDecimal;
import java.math.BigInteger;
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
 * من غير فواصل. هنا الأرقام بفواصل الآلاف ({@code 1,234,567.5})، والتواريخ بنفس صيغ تقرير الـ Excel
 * ({@code 2026-11-26}، {@code 2026-11-26 10:30:00}، {@code 10:30:00}). الفواصل والأرقام لاتينية في
 * كل لغات الجهاز، فالتقرير واحد على أي جهاز.</p>
 *
 * <p>الرقم ما بيتقرّبش: {@code BigDecimal} بيفضل بكل خاناته العشرية زي ما اتكتب ({@code 1,200.50})،
 * والـ {@code double} بأقصر صورة بتمثّله ({@code 7,200} مش {@code 7,200.0}).</p>
 *
 * <p>كائن لكل تقرير: الـ {@link DecimalFormat} مش آمن بين الـ threads، وبيتعمل مرة واحدة لكل عدد خانات.</p>
 */
final class CellText {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);

    private final Map<Integer, DecimalFormat> byScale = new HashMap<>();

    String of(Object value) {
        return switch (value) {
            case null -> "";
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
            // أنواع java.sql بترث java.util.Date، وtoInstant() فيها بترمي: لازم تتفحص قبلها.
            case java.sql.Date date -> DATE.format(date.toLocalDate());
            case java.sql.Time time -> TIME.format(time.toLocalTime());
            case java.sql.Timestamp timestamp -> DATE_TIME.format(timestamp.toLocalDateTime());
            case Date date -> DATE_TIME.format(LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()));
            case Calendar calendar -> DATE_TIME.format(
                    LocalDateTime.ofInstant(calendar.toInstant(), calendar.getTimeZone().toZoneId()));
            case LocalDate date -> DATE.format(date);
            case LocalDateTime dateTime -> DATE_TIME.format(dateTime);
            case LocalTime time -> TIME.format(time);
            case Instant instant -> DATE_TIME.format(LocalDateTime.ofInstant(instant, ZoneId.systemDefault()));
            case ZonedDateTime zoned -> DATE_TIME.format(zoned.toLocalDateTime());
            case OffsetDateTime offset -> DATE_TIME.format(offset.toLocalDateTime());
            default -> String.valueOf(value);
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
