package com.craftclassic.client.assets;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class BitmapTest
{
    @Test
    void rawConstructorKeepsThePixelArray()
    {
        int[] pixels = {0x112233, 0x445566, 0x778899, 0xAABBCC};
        Bitmap bitmap = new Bitmap(2, 2, pixels);

        assertEquals(2, bitmap.width);
        assertEquals(2, bitmap.height);
        assertSame(pixels, bitmap.getTextures());
        assertEquals(0x112233, bitmap.getPixel(0, 0));
        assertEquals(0x445566, bitmap.getPixel(1, 0));
        assertEquals(0x778899, bitmap.getPixel(0, 1));
        assertEquals(0xAABBCC, bitmap.getPixel(1, 1));
    }

    @Test
    void imageConstructorCopiesRowsIntoTheAtlasArray()
    {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFFFF0000);
        image.setRGB(1, 0, 0xFF00FF00);
        image.setRGB(0, 1, 0xFF0000FF);
        image.setRGB(1, 1, 0xFFFFFFFF);

        Bitmap bitmap = new Bitmap(image);

        assertEquals(2, bitmap.width);
        assertEquals(2, bitmap.height);
        assertEquals(4, bitmap.getTextures().length);
        // The image is converted to TYPE_INT_RGB, so alpha is dropped.
        assertEquals(0xFF0000, bitmap.getPixel(0, 0));
        assertEquals(0x00FF00, bitmap.getPixel(1, 0));
        assertEquals(0x0000FF, bitmap.getPixel(0, 1));
        assertEquals(0xFFFFFF, bitmap.getPixel(1, 1));
    }

    @Test
    void blockAtlasLoadsWithTheExpectedStripLayout()
    {
        assertNotNull(Textures.blockTextures, "block atlas should load from resources");
        assertEquals(16, Textures.blockTextures.width);
        assertEquals(768, Textures.blockTextures.height);
        assertEquals(16 * 768, Textures.blockTextures.getTextures().length);
    }

    @Test
    void fontAtlasIsAWholeNumberOfGlyphs()
    {
        assertNotNull(Textures.fontTilesheet, "font atlas should load from resources");
        assertEquals(130, Textures.fontTilesheet.width); // 26 glyphs * 5 px
        assertEquals(24, Textures.fontTilesheet.height); // 4 rows * 6 px
    }

    @Test
    void playerAndParticleAtlasesLoad()
    {
        assertNotNull(Textures.playerTextures);
        assertEquals(32, Textures.playerTextures.width);
        assertEquals(64, Textures.playerTextures.height);

        assertNotNull(Textures.particleTilesheet);
        assertEquals(96, Textures.particleTilesheet.width);
        assertEquals(80, Textures.particleTilesheet.height);
    }
}
