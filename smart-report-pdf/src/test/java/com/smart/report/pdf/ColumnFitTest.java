package com.smart.report.pdf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ColumnFitTest {

    @Test
    void widthsFollowTheWeightsWhenEveryColumnHasRoom() {
        assertArrayEquals(new float[]{100f, 300f},
                ColumnFit.fit(new float[]{1f, 3f}, new float[]{10f, 10f}, 400f), 0.01f);
    }

    /** العمود اللي أطول كلمة فيه أعرض من نصيبه بياخد عرضها، والباقي بيتقسّم على غيره بأوزانه. */
    @Test
    void columnIsNeverNarrowerThanItsWidestWord() {
        float[] widths = ColumnFit.fit(new float[]{1f, 1f, 2f}, new float[]{160f, 10f, 10f}, 400f);

        assertEquals(160f, widths[0], 0.01f);
        assertEquals(80f, widths[1], 0.01f);
        assertEquals(160f, widths[2], 0.01f);
    }

    @Test
    void whenEvenTheNarrowestDoNotFitTheWidthIsSharedInTheirProportion() {
        float[] widths = ColumnFit.fit(new float[]{1f, 1f}, new float[]{300f, 100f}, 200f);

        assertArrayEquals(new float[]{150f, 50f}, widths, 0.01f);
    }

    @Test
    void widthsAlwaysAddUpToTheTable() {
        float[] widths = ColumnFit.fit(new float[]{22f, 14f, 20f, 16f, 22f, 30f},
                new float[]{60f, 20f, 55f, 30f, 48f, 70f}, 367.5f);
        float sum = 0;
        for (float width : widths) {
            sum += width;
        }
        assertEquals(367.5f, sum, 0.01f);
    }
}
