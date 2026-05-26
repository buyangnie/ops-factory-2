/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.knowledge.common.util;

/**
 * The TokenEstimator.
 * @author x00000000
 * @since 2026-05-26
 */

public final class TokenEstimator {

    private TokenEstimator() {
    }

    public static int estimate(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        String normalized = text.trim();
        int whitespaceWords = normalized.split("\\s+").length;
        int cjkChars = (int) normalized.codePoints().filter(TokenEstimator::isCjk).count();
        return Math.max(whitespaceWords, cjkChars / 2);
    }

    private static boolean isCjk(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN;
    }
}
