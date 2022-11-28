/*
 * Copyright (c) 2022  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.util;

import android.view.GestureDetector;
import android.view.MotionEvent;

import androidx.annotation.NonNull;

/**
 * Workaround for inconsistent nullability constraint
 */
public class SimpleOnGestureListener extends GestureDetector.SimpleOnGestureListener {

    /**
     * I have received many crash reports caused by the first parameter e1 being null.
     * It is likely that some manufacturers have messed up either GestureDetector, or
     * event dispatching itself.
     *
     * Normally, this would have been easy to handle by making e1 nullable in Kotlin,
     * but since API 33, e1 is marked as @NonNull in SDK. Hence, Kotlin won't let us
     * declare it nullable while overriding.
     *
     * So we have to take a detour through Java.
     */
    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return e1 != null && e2 != null && checkedOnScroll(e1, e2, distanceX, distanceY);
    }

    public boolean checkedOnScroll(@NonNull MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
        return false;
    }
}
