package com.lepramim.app;

final class FreePlanPolicy {
    static final int FREE_SCREEN_READS_PER_DAY = 12;
    static final int FREE_IMAGE_READS_PER_DAY = 3;

    private FreePlanPolicy() {
    }

    static boolean canConsume(int used, int limit) {
        return used >= 0 && used < limit;
    }

    static int remaining(int used, int limit) {
        return Math.max(0, limit - Math.max(0, used));
    }
}
