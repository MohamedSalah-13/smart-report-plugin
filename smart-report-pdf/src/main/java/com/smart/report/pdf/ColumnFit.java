package com.smart.report.pdf;

/** توزيع عرض الجدول على أعمدته. */
final class ColumnFit {

    private ColumnFit() {
    }

    /**
     * عرض كل عمود بالنقطة: بنسبة وزنه، بشرط ألا ينزل عمود عن {@code narrowest} بتاعه (أطول كلمة فيه
     * وحشوة خليته). العمود اللي اتثبّت على أضيق عرضه بياخده، والباقي بيتقسّم على الأعمدة الأخرى
     * بأوزانها. ولو حتى أضيق العروض مجموعها أكبر من {@code total}، كل عمود بياخد نصيبه منها
     * بنسبة أضيق عرضه.
     *
     * <p>ليه العرض بيتحدد هنا ومش وقت الرسم: السطور بتتقاس وتتكسر على عرض العمود قبل ما تتشكّل،
     * فلازم العرض اللي اتقاست عليه يبقى هو نفسه اللي هتترسم فيه.</p>
     */
    static float[] fit(float[] weights, float[] narrowest, float total) {
        int columns = weights.length;
        float[] points = new float[columns];
        float weightSum = 0;
        float narrowestSum = 0;
        for (int i = 0; i < columns; i++) {
            weightSum += weights[i];
            narrowestSum += narrowest[i];
        }
        if (columns == 0 || weightSum <= 0 || total <= 0) {
            return points;
        }
        if (narrowestSum >= total) {
            for (int i = 0; i < columns; i++) {
                points[i] = total * narrowest[i] / narrowestSum;
            }
            return points;
        }
        boolean[] held = new boolean[columns];
        boolean changed = true;
        while (changed) {
            changed = false;
            float left = total;
            float shared = 0;
            for (int i = 0; i < columns; i++) {
                if (held[i]) {
                    left -= narrowest[i];
                } else {
                    shared += weights[i];
                }
            }
            for (int i = 0; i < columns; i++) {
                if (held[i]) {
                    points[i] = narrowest[i];
                    continue;
                }
                points[i] = shared <= 0 ? 0 : left * weights[i] / shared;
                if (points[i] < narrowest[i]) {
                    held[i] = true;
                    changed = true;
                }
            }
        }
        return points;
    }
}
