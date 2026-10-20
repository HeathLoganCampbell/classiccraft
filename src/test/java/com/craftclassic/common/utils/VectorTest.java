package com.craftclassic.common.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorTest
{
    private static final float EPSILON = 1e-6f;

    @Test
    void dotProductMultipliesMatchingComponents()
    {
        Vector a = new Vector(1, 2, 3);
        Vector b = new Vector(4, 5, 6);

        assertEquals(32.0f, a.dot(b), EPSILON);
    }

    @Test
    void dotProductIsSymmetric()
    {
        Vector a = new Vector(-2, 0.5f, 7);
        Vector b = new Vector(3, -4, 0.25f);

        assertEquals(b.dot(a), a.dot(b), EPSILON);
    }

    @Test
    void addAndSubtractMutateInPlace()
    {
        Vector vector = new Vector(1, 2, 3);

        vector.add(new Vector(10, 20, 30));

        assertEquals(11, vector.getX(), EPSILON);
        assertEquals(22, vector.getY(), EPSILON);
        assertEquals(33, vector.getZ(), EPSILON);

        vector.subtract(new Vector(1, 2, 3));

        assertEquals(10, vector.getX(), EPSILON);
        assertEquals(20, vector.getY(), EPSILON);
        assertEquals(30, vector.getZ(), EPSILON);
    }

    @Test
    void scalarScalesEveryComponent()
    {
        Vector vector = new Vector(2, -3, 0.5f);

        vector.scalar(2);

        assertEquals(4, vector.getX(), EPSILON);
        assertEquals(-6, vector.getY(), EPSILON);
        assertEquals(1, vector.getZ(), EPSILON);
    }

    @Test
    void perAxisScalarScalesIndependently()
    {
        Vector vector = new Vector(2, -3, 0.5f);

        vector.scalar(2, 3, -4);

        assertEquals(4, vector.getX(), EPSILON);
        assertEquals(-9, vector.getY(), EPSILON);
        assertEquals(-2, vector.getZ(), EPSILON);
    }

    @Test
    void zeroVectorIsZero()
    {
        assertTrue(new Vector(0, 0, 0).isZero());
    }

    @Test
    void smallComponentsCountAsZero()
    {
        // isZero is used to suppress the sprint FOV change, so it deliberately
        // has a little slack rather than testing for exact equality.
        assertTrue(new Vector(0.0005f, 0, 0).isZero());
        assertTrue(new Vector(0, -0.0005f, 0).isZero());
    }

    @Test
    void meaningfulMovementIsNotZero()
    {
        assertFalse(new Vector(0.002f, 0, 0).isZero());
        assertFalse(new Vector(0, 1, 0).isZero());
        assertFalse(new Vector(0, 0, -1).isZero());
        assertFalse(new Vector(-1, 0, 0).isZero());
    }
}
