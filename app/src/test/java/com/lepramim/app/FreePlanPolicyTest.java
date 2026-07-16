package com.lepramim.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FreePlanPolicyTest {
    @Test
    public void allowsReadsBeforeDailyLimit() {
        assertTrue(FreePlanPolicy.canConsume(0, FreePlanPolicy.FREE_SCREEN_READS_PER_DAY));
        assertTrue(FreePlanPolicy.canConsume(11, FreePlanPolicy.FREE_SCREEN_READS_PER_DAY));
    }

    @Test
    public void blocksReadsAtDailyLimit() {
        assertFalse(FreePlanPolicy.canConsume(12, FreePlanPolicy.FREE_SCREEN_READS_PER_DAY));
        assertFalse(FreePlanPolicy.canConsume(3, FreePlanPolicy.FREE_IMAGE_READS_PER_DAY));
    }

    @Test
    public void neverReturnsNegativeRemainingReads() {
        assertEquals(0, FreePlanPolicy.remaining(70, FreePlanPolicy.FREE_SCREEN_READS_PER_DAY));
        assertEquals(1, FreePlanPolicy.remaining(2, FreePlanPolicy.FREE_IMAGE_READS_PER_DAY));
    }
}
