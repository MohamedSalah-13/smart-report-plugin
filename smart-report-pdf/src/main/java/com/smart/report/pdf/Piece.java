package com.smart.report.pdf;

import java.io.IOException;

/**
 * حاجة بتترسم في مكان واحد، بطول معروف. القطعة اللي ما تكفيهاش الباقي بتتنقل كلها لصفحة جديدة لو صفحة
 * تكفيها، وإلا بتتقسم بين سطورها ({@link #slice}) على قد المكان. القطعة اللي سطر واحد ما بتتقسمش: بتترسم
 * كلها في أول صفحة جديدة.
 */
interface Piece {

    float height();

    /** يرسم القطعة وأعلاها عند {@code top}. */
    void draw(Canvas canvas, float top) throws IOException;

    /** عدد السطور اللي القطعة ممكن تتقسم بينها؛ 1 = ما بتتقسمش. */
    default int lineCount() {
        return 1;
    }

    /** كام سطر من أولها يكفيهم {@code room} لو اتقسمت هنا؛ أقل من 1 = ولا سطر. */
    default int fitting(float room) {
        return 0;
    }

    /** سطورها من {@code from} لحد قبل {@code to}، قطعة لوحدها: جزء من صف بيكمل على صفحة تانية. */
    default Piece slice(int from, int to) {
        return this;
    }
}
