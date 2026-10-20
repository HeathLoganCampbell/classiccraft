package com.craftclassic.common.world.noise;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoiseTest
{
    private static final double EPSILON = 1e-12;

    @Test
    void perlinNoiseIsDeterministicForASeed()
    {
        PerlinNoise a = new PerlinNoise(new Random(1));
        PerlinNoise b = new PerlinNoise(new Random(1));

        for (int i = 0; i < 200; i++)
        {
            double x = i * 0.37;
            double z = i * 0.61;
            assertEquals(a.compute(x, z), b.compute(x, z), 0.0);
        }
    }

    @Test
    void differentSeedsGiveDifferentPerlinNoise()
    {
        PerlinNoise a = new PerlinNoise(new Random(1));
        PerlinNoise b = new PerlinNoise(new Random(2));

        boolean differs = false;
        for (int i = 0; i < 20 && !differs; i++)
        {
            differs = a.compute(i * 0.37, i * 0.61) != b.compute(i * 0.37, i * 0.61);
        }

        assertTrue(differs, "two seeds produced identical samples");
    }

    @Test
    void perlinSamplesStayInsideTheUnitRange()
    {
        PerlinNoise noise = new PerlinNoise(new Random(1));

        for (int i = 0; i < 100; i++)
        {
            for (int j = 0; j < 100; j++)
            {
                double value = noise.compute(i * 0.37, j * 0.61);
                assertTrue(value >= -1.0 && value <= 1.0, "out of range: " + value);
            }
        }
    }

    @Test
    void valueAtAnIntegerLatticePointIsZero()
    {
        // The gradient noise vanishes on the lattice it is built from.
        PerlinNoise noise = new PerlinNoise(new Random(3));

        assertEquals(0.0, noise.compute(0, 0), 1e-12);
        assertEquals(0.0, noise.compute(4, 7), 1e-12);
    }

    @Test
    void singleOctaveMatchesASinglePerlinLayer()
    {
        Random perlinSeed = new Random(5);
        Random octaveSeed = new Random(5);

        PerlinNoise perlin = new PerlinNoise(perlinSeed);
        OctaveNoise octave = new OctaveNoise(octaveSeed, 1);

        for (int i = 0; i < 50; i++)
        {
            double x = i * 0.73;
            double z = i * 0.29;
            assertEquals(perlin.compute(x, z), octave.compute(x, z), 0.0);
        }
    }

    @Test
    void octavesAreWeightedSumsOfDoublingFrequency()
    {
        Random layers = new Random(9);
        PerlinNoise first = new PerlinNoise(layers);
        PerlinNoise second = new PerlinNoise(layers);

        OctaveNoise octave = new OctaveNoise(new Random(9), 2);

        for (int i = 0; i < 50; i++)
        {
            double x = i * 0.43;
            double z = i * 0.17;

            double expected = first.compute(x, z)
                    + 2.0 * second.compute(x / 2.0, z / 2.0);

            assertEquals(expected, octave.compute(x, z), EPSILON);
        }
    }

    @Test
    void combinedNoiseOffsetsTheFirstNoiseByTheSecond()
    {
        Noise first = new Noise()
        {
            @Override
            public double compute(double x, double z)
            {
                return x * 2.0 + z;
            }
        };

        Noise second = new Noise()
        {
            @Override
            public double compute(double x, double z)
            {
                return x - z;
            }
        };

        CombinedNoise combined = new CombinedNoise(first, second);

        // first(x + second(x, z), z) = 2 * (x + x - z) + z
        assertEquals(4.0 * 3.0 - 2.0 * 1.0 + 1.0, combined.compute(3, 1), EPSILON);
    }

    @Test
    void octaveNoiseIsDeterministic()
    {
        OctaveNoise a = new OctaveNoise(new Random(11), 4);
        OctaveNoise b = new OctaveNoise(new Random(11), 4);

        assertEquals(a.compute(1.5, -2.25), b.compute(1.5, -2.25), 0.0);
        assertTrue(a.compute(1.5, -2.25)
                != new OctaveNoise(new Random(12), 4).compute(1.5, -2.25));
    }
}
