package com.smart.report.pdf;

import com.smart.report.ReportException;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/** قطع الجزء بالترتيب، واحدة واحدة: الجدول بيقيس كل صف وهو جاي، فآلاف الصفوف ما بتتقاسش مرة واحدة. */
@FunctionalInterface
interface Pieces {

    /** القطعة الجاية، أو {@code null} بعد آخر قطعة. */
    Piece next() throws IOException, ReportException;

    static Pieces of(List<Piece> pieces) {
        Iterator<Piece> each = pieces.iterator();
        return () -> each.hasNext() ? each.next() : null;
    }
}
