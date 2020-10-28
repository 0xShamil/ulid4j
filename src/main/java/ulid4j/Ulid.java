/*
 * The MIT License
 *
 * Copyright (C) 2020 Shamil
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package ulid4j;

import java.security.SecureRandom;

/**
 * @author shamil
 */
public final class Ulid {
    private static final char[] ENCODING_CHARS = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K',
            'M', 'N', 'P', 'Q', 'R', 'S', 'T', 'V', 'W', 'X',
            'Y', 'Z',
    };
    private static final int MASK = 0x1F;
    private static final int MASK_BITS = 5;
    private static final long TIMESTAMP_OVERFLOW_MASK = 0xFFFF_0000_0000_0000L;
    private static final long TIMESTAMP_MSB_MASK = 0xFFFF_FFFF_FFFF_0000L;
    private static final long RANDOM_MSB_MASK = 0xFFFFL;
    private static final long HALF_RANDOM_COMPONENT = 0x000000ffffffffffL;
    private static final long MAX_INCREMENT = 0x0000010000000000L;

    // Date: +10889-08-02T05:31:50.655Z
    // epoch time: 281474976710655
    private static final long TIMESTAMP_MAX = (long) Math.pow(2, 48) - 1;

    // SipHash constants
    private static final byte A = 0b00000110;
    private static final byte B = 0b01111111;
    private static final byte C = (byte) 0b10111000;
    private static final byte D = (byte) 0b11000001;
    // SipHash state
    private long v0 = 0;
    private long v1 = 0;
    private long v2 = 0;
    private long v3 = 0;

    private long lastUsedTimestamp;
    private long randomMaxMsb;
    private long randomMaxLsb;
    private long randomMsb = 0;
    private long randomLsb = 0;

    // Underlying PRNG
    private final SecureRandom randomGenerator;

    public Ulid() {
        this(new SecureRandom());
    }

    public Ulid(SecureRandom random) {
        this.randomGenerator = random;
        reseed(); // do an initial seeding
    }

    /**
     * Returns a ULID string, encoded to Crockford's base32 notation.
     */
    public String create() {
        final long k0 = sipHash24(v0, v1, v2, v3, A);
        final long k1 = sipHash24(v0, v1, v2, v3, B);
        final long msb = (sipHash24(v0, v1, v2, v3, C) & ~0xF000L) | 0x4000L;
        final long lsb = ((sipHash24(v0, v1, v2, v3, D) << 2) >>> 2) | 0x8000000000000000L;
        reseed(k0, k1);

        return asString(System.currentTimeMillis(), msb, lsb);
    }

    /**
     * Returns a monotonically increasing ULID string, encoded to Crockford's base32 notation.
     */
    public String next() {
        final long timestamp = getTimestamp();
        final long msbRandom = randomMsb & HALF_RANDOM_COMPONENT;
        final long lsbRandom = randomLsb & HALF_RANDOM_COMPONENT;
        final long msb = (timestamp << 16) | (msbRandom >>> 24);
        final long lsb = (msbRandom << 40) | lsbRandom;

        return asString(msb, lsb);
    }

    public void reseed() {
        byte[] seed = new byte[128];
        randomGenerator.nextBytes(seed);
        reseed(randomGenerator.nextLong(), randomGenerator.nextLong());
    }

    private void reseed(long k0, long k1) {
        // SipHash magic constants
        v0 = k0 ^ 0x736F6D6570736575L;
        v1 = k1 ^ 0x646F72616E646F6DL;
        v2 = k0 ^ 0x6C7967656E657261L;
        v3 = k1 ^ 0x7465646279746573L;
    }

    /**
     * Return the current timestamp and resets or increments the random part.
     *
     * @return timestamp
     */
    private long getTimestamp() {
        final long timestamp = System.currentTimeMillis();
        if (timestamp == lastUsedTimestamp) {
            // if this is the same millisecond, just increment the random part
            increment();
        } else {
            // millisecond changed, regenerate the random part
            reset();
        }
        lastUsedTimestamp = timestamp;
        return timestamp;
    }

    /**
     * Reset the random part of the GUID.
     */
    private synchronized void reset() {
        // Generate random values
        final long k0 = sipHash24(v0, v1, v2, v3, A);
        final long k1 = sipHash24(v0, v1, v2, v3, B);
        randomMsb = (sipHash24(v0, v1, v2, v3, C) & ~0xF000L) | 0x4000L;
        randomLsb = ((sipHash24(v0, v1, v2, v3, D) << 2) >>> 2) | 0x8000000000000000L;
        reseed(k0, k1);

        // Save the random values
        randomMaxMsb = randomMsb | MAX_INCREMENT;
        randomMaxLsb = randomLsb | MAX_INCREMENT;
    }

    /**
     * Increment the random part of the GUID.
     */
    private synchronized void increment() {
        if (++randomLsb >= randomMaxLsb) {
            randomLsb = randomLsb & HALF_RANDOM_COMPONENT;
            if (++randomMsb >= randomMaxMsb) {
                reset();
            }
        }
    }

    private static String asString(long timestamp, long msb, long lsb) {
        checkTimestamp(timestamp);
        return crockfordBase32(timestamp, msb, lsb);
    }

    private static String asString(long msb, long lsb) {
        final long time = ((msb & TIMESTAMP_MSB_MASK) >>> 16);
        final long random1 = ((msb & RANDOM_MSB_MASK) << 24) | ((lsb & 0xffffff0000000000L) >>> 40);
        final long random2 = (lsb & 0x000000ffffffffffL);
        return crockfordBase32(time, random1, random2);
    }

    private static String crockfordBase32(long timeComponent, long msb, long lsb) {
        char[] buffer = new char[26];

        // first 10 characters from timestamp
        buffer[0] = ENCODING_CHARS[(int) ((timeComponent >>> ((9) * MASK_BITS)) & MASK)];
        buffer[1] = ENCODING_CHARS[(int) ((timeComponent >>> ((8) * MASK_BITS)) & MASK)];
        buffer[2] = ENCODING_CHARS[(int) ((timeComponent >>> ((7) * MASK_BITS)) & MASK)];
        buffer[3] = ENCODING_CHARS[(int) ((timeComponent >>> ((6) * MASK_BITS)) & MASK)];
        buffer[4] = ENCODING_CHARS[(int) ((timeComponent >>> ((5) * MASK_BITS)) & MASK)];
        buffer[5] = ENCODING_CHARS[(int) ((timeComponent >>> ((4) * MASK_BITS)) & MASK)];
        buffer[6] = ENCODING_CHARS[(int) ((timeComponent >>> ((3) * MASK_BITS)) & MASK)];
        buffer[7] = ENCODING_CHARS[(int) ((timeComponent >>> ((2) * MASK_BITS)) & MASK)];
        buffer[8] = ENCODING_CHARS[(int) ((timeComponent >>> ((1) * MASK_BITS)) & MASK)];
        buffer[9] = ENCODING_CHARS[(int) ((timeComponent >>> ((0) * MASK_BITS)) & MASK)];

        buffer[10] = ENCODING_CHARS[(int) ((msb >>> ((7) * MASK_BITS)) & MASK)];
        buffer[11] = ENCODING_CHARS[(int) ((msb >>> ((6) * MASK_BITS)) & MASK)];
        buffer[12] = ENCODING_CHARS[(int) ((msb >>> ((5) * MASK_BITS)) & MASK)];
        buffer[13] = ENCODING_CHARS[(int) ((msb >>> ((4) * MASK_BITS)) & MASK)];
        buffer[14] = ENCODING_CHARS[(int) ((msb >>> ((3) * MASK_BITS)) & MASK)];
        buffer[15] = ENCODING_CHARS[(int) ((msb >>> ((2) * MASK_BITS)) & MASK)];
        buffer[16] = ENCODING_CHARS[(int) ((msb >>> ((1) * MASK_BITS)) & MASK)];
        buffer[17] = ENCODING_CHARS[(int) ((msb >>> ((0) * MASK_BITS)) & MASK)];

        buffer[18] = ENCODING_CHARS[(int) ((lsb >>> ((7) * MASK_BITS)) & MASK)];
        buffer[19] = ENCODING_CHARS[(int) ((lsb >>> ((6) * MASK_BITS)) & MASK)];
        buffer[20] = ENCODING_CHARS[(int) ((lsb >>> ((5) * MASK_BITS)) & MASK)];
        buffer[21] = ENCODING_CHARS[(int) ((lsb >>> ((4) * MASK_BITS)) & MASK)];
        buffer[22] = ENCODING_CHARS[(int) ((lsb >>> ((3) * MASK_BITS)) & MASK)];
        buffer[23] = ENCODING_CHARS[(int) ((lsb >>> ((2) * MASK_BITS)) & MASK)];
        buffer[24] = ENCODING_CHARS[(int) ((lsb >>> ((1) * MASK_BITS)) & MASK)];
        buffer[25] = ENCODING_CHARS[(int) ((lsb >>> ((0) * MASK_BITS)) & MASK)];

        return new String(buffer);
    }

    // a very slimmed-down version of SipHash-2-4
    // which operates on a single byte
    private static long sipHash24(long v0, long v1, long v2, long v3, byte data) {
        final long m = (data & 0xFFL) | 0x100000000000000L; // simplify the masking

        v3 ^= m;
        for (int i = 0; i < 2; i++) {
            v0 += v1;
            v2 += v3;
            v1 = Long.rotateLeft(v1, 13);
            v3 = Long.rotateLeft(v3, 16);

            v1 ^= v0;
            v3 ^= v2;
            v0 = Long.rotateLeft(v0, 32);

            v2 += v1;
            v0 += v3;
            v1 = Long.rotateLeft(v1, 17);
            v3 = Long.rotateLeft(v3, 21);

            v1 ^= v2;
            v3 ^= v0;
            v2 = Long.rotateLeft(v2, 32);
        }
        v0 ^= m;

        v2 ^= 0xFF;
        for (int i = 0; i < 4; i++) {
            v0 += v1;
            v2 += v3;
            v1 = Long.rotateLeft(v1, 13);
            v3 = Long.rotateLeft(v3, 16);

            v1 ^= v0;
            v3 ^= v2;
            v0 = Long.rotateLeft(v0, 32);

            v2 += v1;
            v0 += v3;
            v1 = Long.rotateLeft(v1, 17);
            v3 = Long.rotateLeft(v3, 21);

            v1 ^= v2;
            v3 ^= v0;
            v2 = Long.rotateLeft(v2, 32);
        }
        return v0 ^ v1 ^ v2 ^ v3;
    }

    private static void checkTimestamp(long timestamp) {
        if ((timestamp & TIMESTAMP_OVERFLOW_MASK) != 0) {
            throw new IllegalArgumentException("ULID does not support timestamps after +10889-08-02T05:31:50.655Z!");
        }
    }

    /**
     * Extract the time component from a ULID String.
     */
    public static long unixTime(String ulidStr) {
        char[] tb = ulidStr.toCharArray();
        final char[] timestampComponent = new char[10];
        System.arraycopy(tb, 0, timestampComponent, 0, 10);
        return toLong(timestampComponent);
    }

    // Validate generated ULID Strings

    /**
     * Checks if the string is a valid ULID.
     */
    public static boolean isValid(String ulidStr) {
        if (ulidStr == null) {
            return false;
        }

        char[] chars = ulidStr.toCharArray();
        if (chars.length != 26 || !containsValidBase32Chars(chars)) {
            return false;
        }

        // Extract time component
        final long timestamp = unixTime(ulidStr);

        return timestamp >= 0 && timestamp <= TIMESTAMP_MAX;
    }

    /**
     * Decode a base 32 char array to a long number.
     */
    protected static long toLong(char[] input) {
        long n = 0;
        for (int i = 0; i < input.length; i++) {
            int d = decodeBase32(input[i]);
            n = 32 * n + d;
        }
        return n;
    }

    private static int decodeBase32(char c) {
        for (int i = 0; i < ENCODING_CHARS.length; i++) {
            if (ENCODING_CHARS[i] == c) {
                return (byte) i;
            }
        }
        return (byte) '0';
    }

    /**
     * Check if all the characters in the array are valid base32 characters.
     */
    private static boolean containsValidBase32Chars(final char[] chars) {
        char[] input = toUpperCase(chars);
        for (int i = 0; i < input.length; i++) {
            if (!isBase32Char(input[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if the supplied character is a valid base32 character.
     */
    private static boolean isBase32Char(char c) {
        for (int j = 0; j < ENCODING_CHARS.length; j++) {
            if (c == ENCODING_CHARS[j]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Convert all characters in the array to their corresponding upper case, if applicable.
     */
    private static char[] toUpperCase(final char[] input) {
        char[] output = new char[input.length];
        for (int i = 0; i < output.length; i++) {
            output[i] = Character.toUpperCase(input[i]);
        }
        return output;
    }
}
