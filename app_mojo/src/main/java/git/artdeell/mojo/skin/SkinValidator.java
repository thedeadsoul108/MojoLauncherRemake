package git.artdeell.mojo.skin;

import java.nio.charset.StandardCharsets;

/**
 * Validates skin PNGs without touching Android graphics, so the rules are unit-testable.
 * Minecraft accepts exactly 64x32 (legacy) and 64x64 (modern) RGBA skins.
 */
public final class SkinValidator {
    public static final int MAX_BYTES = 2 * 1024 * 1024;

    private static final byte[] PNG_SIGNATURE = new byte[]{
            (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'
    };

    private SkinValidator() {
    }

    public enum Reason {
        NOT_PNG,
        TOO_LARGE,
        BAD_DIMENSIONS,
        EMPTY
    }

    public static final class SkinValidationException extends Exception {
        public final Reason reason;

        public SkinValidationException(Reason reason, String message) {
            super(message);
            this.reason = reason;
        }
    }

    public static final class Result {
        public final int width;
        public final int height;

        Result(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public boolean isLegacy() {
            return height == 32;
        }
    }

    public static Result validate(byte[] bytes) throws SkinValidationException {
        if (bytes == null || bytes.length == 0) {
            throw new SkinValidationException(Reason.EMPTY, "Skin file is empty");
        }
        if (bytes.length > MAX_BYTES) {
            throw new SkinValidationException(Reason.TOO_LARGE, "Skin file is too large");
        }
        if (bytes.length < 33) {
            throw new SkinValidationException(Reason.NOT_PNG, "Skin is not a PNG");
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (bytes[i] != PNG_SIGNATURE[i]) {
                throw new SkinValidationException(Reason.NOT_PNG, "Skin is not a PNG");
            }
        }

        int offset = PNG_SIGNATURE.length;
        while (offset + 12 <= bytes.length) {
            int length = readInt(bytes, offset);
            if (length < 0 || offset + 12 + length > bytes.length) {
                throw new SkinValidationException(Reason.NOT_PNG, "Malformed PNG chunk");
            }
            String type = new String(bytes, offset + 4, 4, StandardCharsets.US_ASCII);
            if ("IHDR".equals(type)) {
                if (length != 13) {
                    throw new SkinValidationException(Reason.NOT_PNG, "Malformed PNG header");
                }
                int width = readInt(bytes, offset + 8);
                int height = readInt(bytes, offset + 12);
                if (width != 64 || (height != 32 && height != 64)) {
                    throw new SkinValidationException(Reason.BAD_DIMENSIONS,
                            "Expected a 64x32 or 64x64 PNG, got " + width + "x" + height);
                }
                return new Result(width, height);
            }
            if ("IEND".equals(type)) break;
            offset += 12 + length;
        }
        throw new SkinValidationException(Reason.NOT_PNG, "PNG has no IHDR chunk");
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 24)
                | ((bytes[offset + 1] & 0xff) << 16)
                | ((bytes[offset + 2] & 0xff) << 8)
                | (bytes[offset + 3] & 0xff);
    }
}
