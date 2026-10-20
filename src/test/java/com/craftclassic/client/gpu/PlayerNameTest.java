package com.craftclassic.client.gpu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerNameTest
{
    @Test
    void commandLineWins()
    {
        assertEquals("CLI", GpuMain.resolveName("CLI", "ENV"));
    }

    @Test
    void environmentIsUsedWithoutTheFlag()
    {
        assertEquals("ENV", GpuMain.resolveName(null, "ENV"));
        assertEquals("ENV", GpuMain.resolveName("  ", "  ENV  "));
    }

    @Test
    void fallsBackToPlayer()
    {
        assertEquals("Player", GpuMain.resolveName(null, null));
        assertEquals("Player", GpuMain.resolveName("", ""));
    }
}
