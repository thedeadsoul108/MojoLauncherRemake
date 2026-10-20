package git.artdeell.mojo.skin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class SkinValidatorTest {

    @Test
    public void acceptsModernAndLegacySkins() throws Exception {
        SkinValidator.Result modern = SkinValidator.validate(png(64, 64));
        assertEquals(64, modern.width);
        assertEquals(64, modern.height);
        assertFalse(modern.isLegacy());

        SkinValidator.Result legacy = SkinValidator.validate(png(64, 32));
        assertTrue(legacy.isLegacy());
    }

    @Test
    public void rejectsNonPngBytes() {
        assertReason(SkinValidator.Reason.NOT_PNG, new byte[]{1, 2, 3, 4});
    }

    @Test
    public void rejectsWrongDimensions() {
        assertReason(SkinValidator.Reason.BAD_DIMENSIONS, png(32, 32));
        assertReason(SkinValidator.Reason.BAD_DIMENSIONS, png(128, 128));
        assertReason(SkinValidator.Reason.BAD_DIMENSIONS, png(64, 16));
    }

    @Test
    public void rejectsOversizedFilesAndEmptyInput() {
        assertReason(SkinValidator.Reason.EMPTY, new byte[0]);
        byte[] oversized = new byte[SkinValidator.MAX_BYTES + 1];
        oversized[0] = (byte) 0x89;
        oversized[1] = 'P';
        assertReason(SkinValidator.Reason.TOO_LARGE, oversized);
    }

    private static void assertReason(SkinValidator.Reason reason, byte[] bytes) {
        try {
            SkinValidator.validate(bytes);
            fail("Expected " + reason);
        } catch (SkinValidator.SkinValidationException e) {
            assertEquals(reason, e.reason);
        }
    }

    /** Builds the signature and IHDR chunk the validator inspects. */
    static byte[] png(int width, int height) {
        byte[] header = new byte[8 + 25];
        byte[] signature = new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
        System.arraycopy(signature, 0, header, 0, signature.length);
        writeInt(header, 8, 13);
        header[12] = 'I';
        header[13] = 'H';
        header[14] = 'D';
        header[15] = 'R';
        writeInt(header, 16, width);
        writeInt(header, 20, height);
        header[24] = 8;
        header[25] = 6;
        return header;
    }

    private static void writeInt(byte[] target, int offset, int value) {
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }
}
