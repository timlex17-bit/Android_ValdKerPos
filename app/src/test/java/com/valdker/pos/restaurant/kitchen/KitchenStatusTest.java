package com.valdker.pos.restaurant.kitchen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/** Tabel transisi di sini harus sama persis dengan tabel di kontrak API. */
public class KitchenStatusTest {

    @Test
    public void forwardStepsMatchTheContractTable() {
        assertEquals(Arrays.asList(KitchenStatus.PREPARING, KitchenStatus.CANCELLED),
                KitchenStatus.allowedNext(KitchenStatus.PENDING));
        assertEquals(Arrays.asList(KitchenStatus.READY, KitchenStatus.CANCELLED),
                KitchenStatus.allowedNext(KitchenStatus.PREPARING));
        assertEquals(Arrays.asList(KitchenStatus.SERVED, KitchenStatus.CANCELLED),
                KitchenStatus.allowedNext(KitchenStatus.READY));
    }

    @Test
    public void terminalStatesOfferNothing() {
        assertEquals(Collections.emptyList(), KitchenStatus.allowedNext(KitchenStatus.SERVED));
        assertEquals(Collections.emptyList(), KitchenStatus.allowedNext(KitchenStatus.CANCELLED));
    }

    @Test
    public void servedCannotBeCancelled() {
        // Satu-satunya status yang tidak bisa dibatalkan.
        assertFalse(KitchenStatus.canMove(KitchenStatus.SERVED, KitchenStatus.CANCELLED));
        assertTrue(KitchenStatus.canMove(KitchenStatus.PENDING, KitchenStatus.CANCELLED));
        assertTrue(KitchenStatus.canMove(KitchenStatus.PREPARING, KitchenStatus.CANCELLED));
        assertTrue(KitchenStatus.canMove(KitchenStatus.READY, KitchenStatus.CANCELLED));
    }

    @Test
    public void skippingAndGoingBackwardsAreBothRefused() {
        assertFalse(KitchenStatus.canMove(KitchenStatus.PENDING, KitchenStatus.READY));
        assertFalse(KitchenStatus.canMove(KitchenStatus.PENDING, KitchenStatus.SERVED));
        assertFalse(KitchenStatus.canMove(KitchenStatus.PREPARING, KitchenStatus.SERVED));
        assertFalse(KitchenStatus.canMove(KitchenStatus.READY, KitchenStatus.PENDING));
        assertFalse(KitchenStatus.canMove(KitchenStatus.READY, KitchenStatus.PREPARING));
        assertFalse(KitchenStatus.canMove(KitchenStatus.PREPARING, KitchenStatus.PENDING));
    }

    @Test
    public void movingToItselfIsNotATransition() {
        assertFalse(KitchenStatus.canMove(KitchenStatus.PENDING, KitchenStatus.PENDING));
        assertFalse(KitchenStatus.canMove(KitchenStatus.READY, KitchenStatus.READY));
    }

    @Test
    public void nullStatusIsNotAFifthState() {
        // Item retail/workshop: tidak pernah antre di dapur, jadi tidak ada
        // aksi yang ditawarkan - bukan diperlakukan seperti PENDING.
        assertEquals(Collections.emptyList(), KitchenStatus.allowedNext(null));
        assertFalse(KitchenStatus.isKnown(null));
        assertFalse(KitchenStatus.isKnown(""));
        assertFalse(KitchenStatus.canMove(null, KitchenStatus.PREPARING));
    }

    @Test
    public void caseAndWhitespaceAreTolerated() {
        assertTrue(KitchenStatus.canMove(" pending ", "preparing"));
        assertEquals(KitchenStatus.READY, KitchenStatus.normalize("  ready "));
        assertTrue(KitchenStatus.isKnown("Served"));
    }

    @Test
    public void terminalCheckCoversBothEnds() {
        assertTrue(KitchenStatus.isTerminal(KitchenStatus.SERVED));
        assertTrue(KitchenStatus.isTerminal(KitchenStatus.CANCELLED));
        assertFalse(KitchenStatus.isTerminal(KitchenStatus.READY));
        assertFalse(KitchenStatus.isTerminal(null));
    }
}
