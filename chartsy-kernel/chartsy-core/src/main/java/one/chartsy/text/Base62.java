package one.chartsy.text;

import java.util.Arrays;

/**
 * A Base62 encoder/decoder.
 *
 * @author Mariusz Bernacki
 */
public class Base62 {

    public static Encoder getEncoder() {
        return Encoder.DEFAULT;
    }

    public static Decoder getDecoder() {
        return Decoder.DEFAULT;
    }

    public static class Encoder {
        private static final Encoder DEFAULT = new Encoder();

        private static final char[] ENCODE_TABLE = {
                'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P',
                'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f',
                'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v',
                'w', 'x', 'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9'
        };

        /**
         * Encodes binary data using a Base62 algorithm.
         *
         * @param data binary data to encode
         * @return String containing Base62 characters
         */
        public String encode(byte[] data) {
            StringBuilder sb = new StringBuilder(data.length*8/5 + 1);

            BitInputStream in = new BitInputStream(data);
            while (in.hasMore()) {
                // Read no more than 6 bits from the stream
                int rawBits = in.readBits(6);

                // For some cases special processing is needed, so _bits_ will contain final data representation needed to
                // form next output character
                int bits;
                if ((rawBits & COMPACT_MASK) == COMPACT_MASK) {
                    // We can't represent all 6 bits of the data, so extract only least significant 5 bits and return for
                    // one bit back in the stream
                    bits = rawBits & MASK_5BITS;
                    in.seekBit(-1);
                } else {
                    // In most cases all 6 bits used to form output character
                    bits = rawBits;
                }

                // Look up next character in the encoding table and append it to the output StringBuilder
                sb.append(ENCODE_TABLE[bits]);
            }

            return sb.toString();
        }
    }

    public static class Decoder {
        private static final Decoder DEFAULT = new Decoder();

        private static final byte[] DECODE_TABLE = {
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                52, 53, 54, 55, 56, 57, 58, 59, 60, 61, -1, -1, -1, -1, -1, -1,
                -1,  0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14,
                15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, -1, -1, -1, -1, -1,
                -1, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40,
                41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51
        };

        public byte[] decode(String base62String) {
            int length = base62String.length();

            // Create stream with capacity enough to fit
            BitOutputStream out = new BitOutputStream(length * 6);

            int lastCharPos = length - 1;
            for (int i = 0; i < length; i++) {
                // Obtain data bits from decoding table for the next character
                final int bits = decodedBitsForCharacter(base62String.charAt(i));

                // Determine bits count needed to write to the stream
                final int bitsCount;
                if ((bits & COMPACT_MASK) == COMPACT_MASK) {
                    // Compact form detected, write down only 5 bits
                    bitsCount = 5;
                } else if (i >= lastCharPos) {
                    // For the last character write down all bits that needed for the completion of the stream
                    bitsCount = out.getBitsCountUpToByte();
                } else {
                    // In most cases the full 6-bits form will be used
                    bitsCount = 6;
                }

                out.writeBits(bitsCount, bits);
            }

            return out.toArray();
        }

        private static int decodedBitsForCharacter(char character) {
            int result;
            if (character >= DECODE_TABLE.length || (result = DECODE_TABLE[character]) < 0) {
                throw new IllegalArgumentException("Wrong Base62 symbol found: " + character);
            }
            return result;
        }
    }

    /**
     * Special mask for the data that should be written in compact 5-bits form
     */
    private static final int COMPACT_MASK = 0x1E; // 00011110

    /**
     * Mask for extracting 5 bits of the data
     */
    private static final int MASK_5BITS = 0x1F; // 00011111

    private static class BitInputStream {
        private final byte[] buffer;

        private int offset = 0;

        public BitInputStream(byte[] bytes) {
            this.buffer = bytes;
        }

        public void seekBit(int pos) {
            offset += pos;
            if (offset < 0 || offset > buffer.length * 8) {
                throw new IndexOutOfBoundsException();
            }
        }

        public int readBits(int bitsCount) {
            if (bitsCount < 0 || bitsCount > 7) {
                throw new IndexOutOfBoundsException();
            }

            int bitNum = offset % 8;
            int byteNum = offset / 8;

            int firstRead = Math.min(8 - bitNum, bitsCount);
            int secondRead = bitsCount - firstRead;

            int result = (buffer[byteNum] & (((1 << firstRead) - 1) << bitNum)) >>> bitNum;
            if (secondRead > 0 && byteNum + 1 < buffer.length) {
                result |= (buffer[byteNum + 1] & ((1 << secondRead) - 1)) << firstRead;
            }

            offset += bitsCount;

            return result;
        }

        public boolean hasMore() {
            return offset < buffer.length * 8;
        }
    }

    private static class BitOutputStream {
        private final byte[] buffer;

        private int offset = 0;

        public BitOutputStream(int capacity) {
            buffer = new byte[capacity / 8];
        }

        public void writeBits(int bitsCount, int bits) {
            int bitNum = offset % 8;
            int byteNum = offset / 8;

            int firstWrite = Math.min(8 - bitNum, bitsCount);
            int secondWrite = bitsCount - firstWrite;

            buffer[byteNum] |= (bits & ((1 << firstWrite) - 1)) << bitNum;
            if (secondWrite > 0) {
                buffer[byteNum + 1] |= (bits >>> firstWrite) & ((1 << secondWrite) - 1);
            }

            offset += bitsCount;
        }

        public byte[] toArray() {
            int newLength = offset / 8;
            return newLength == buffer.length ? buffer : Arrays.copyOf(buffer, newLength);
        }

        public int getBitsCountUpToByte() {
            int currentBit = offset % 8;
            return currentBit == 0 ? 0 : 8 - currentBit;
        }
    }
}
