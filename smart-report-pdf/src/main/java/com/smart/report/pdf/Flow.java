package com.smart.report.pdf;

import java.util.List;

/** جزء من قطع جاهزة ومسافات ثابتة: أغلب الأجزاء. */
record Flow(float gapBefore, Keep keep, List<Piece> drawn, float gapAfter, Join join) implements Part {

    static Flow of(List<Piece> drawn) {
        return new Flow(0, Keep.NONE, drawn, 0, Join.NONE);
    }

    @Override
    public Pieces pieces() {
        return Pieces.of(drawn);
    }

    Flow withGaps(float before, float after) {
        return new Flow(before, keep, drawn, after, join);
    }

    Flow withKeep(Keep value) {
        return new Flow(gapBefore, value, drawn, gapAfter, join);
    }

    Flow withJoin(Join value) {
        return new Flow(gapBefore, keep, drawn, gapAfter, value);
    }
}
