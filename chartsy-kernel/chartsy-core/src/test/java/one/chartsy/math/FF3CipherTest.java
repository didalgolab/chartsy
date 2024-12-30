package one.chartsy.math;

import one.chartsy.text.Base62;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

class FF3CipherTest {

    private static byte[] longToByteArray(long value) {
        return new byte[] {
                (byte) (value >> 56),
                (byte) (value >> 48),
                (byte) (value >> 40),
                (byte) (value >> 32),
                (byte) (value >> 24),
                (byte) (value >> 16),
                (byte) (value >> 8),
                (byte) value
        };
    }

    private static long byteArrayToLong(byte[] bytes) {
        return new BigInteger(bytes).longValueExact();
    }

    @Test
    void encrypt_is_reversible() throws IllegalBlockSizeException, BadPaddingException {
        FF3Cipher cipher = new FF3Cipher("616AC05515D06DC4D89C9EB1C2EEA74B", "DE4C73B3DFC41396", 62);

        long limit = 1000;
        for (long value = -limit; value <= limit; value++) {
            byte[] valueBytes = longToByteArray(value);
            String valueBase62 = Base62.getEncoder().encode(valueBytes);
            String valueEncrypted = cipher.encrypt(valueBase62);
            String valueDecrypted = cipher.decrypt(valueEncrypted);
            long result = byteArrayToLong(Base62.getDecoder().decode(valueDecrypted));

            //System.out.println(value + ".\t" + valueBase62 + " - " + valueEncrypted + " - " + valueDecrypted + " - " + result);
            assertEquals(value, result);
        }
    }

    /*
     * NIST Test Vectors for 128, 198, and 256 bit modes
     * https://csrc.nist.gov/csrc/media/projects/cryptographic-standards-and-guidelines/documents/examples/ff3samples.pdf
     */
    @ParameterizedTest
    @CsvSource({
            // AES-128 - radix, key, tweak, plaintext, ciphertext
            "10, EF4359D8D580AA4F7F036D6F04FC6A94, D8E7920AFA330A73, 890121234567890000, 750918814058654607",
            "10, EF4359D8D580AA4F7F036D6F04FC6A94, 9A768A92F60E12D8, 890121234567890000, 018989839189395384",
            "10, EF4359D8D580AA4F7F036D6F04FC6A94, D8E7920AFA330A73, 89012123456789000000789000000, 48598367162252569629397416226",
            "10, EF4359D8D580AA4F7F036D6F04FC6A94, 0000000000000000, 89012123456789000000789000000, 34695224821734535122613701434",
            "26, EF4359D8D580AA4F7F036D6F04FC6A94, 9A768A92F60E12D8, 0123456789abcdefghi, g2pk40i992fn20cjakb",
            // AES-192 - radix, key, tweak, plaintext, ciphertext
            "10, EF4359D8D580AA4F7F036D6F04FC6A942B7E151628AED2A6, D8E7920AFA330A73, 890121234567890000, 646965393875028755",
            "10, EF4359D8D580AA4F7F036D6F04FC6A942B7E151628AED2A6, 9A768A92F60E12D8, 890121234567890000, 961610514491424446",
            "10, EF4359D8D580AA4F7F036D6F04FC6A942B7E151628AED2A6, D8E7920AFA330A73, 89012123456789000000789000000, 53048884065350204541786380807",
            "10, EF4359D8D580AA4F7F036D6F04FC6A942B7E151628AED2A6, 0000000000000000, 89012123456789000000789000000, 98083802678820389295041483512",
            "26, EF4359D8D580AA4F7F036D6F04FC6A942B7E151628AED2A6, 9A768A92F60E12D8, 0123456789abcdefghi, i0ihe2jfj7a9opf9p88",
            // AES-256 - radix, key, tweak, plaintext, ciphertext
            "10, EF4359D8D580AA4F7F036D6F04FC6A942B7E151628AED2A6ABF7158809CF4F3C, D8E7920AFA330A73, 890121234567890000, 922011205562777495",
            "10, EF4359D8D580AA4F7F036D6F04FC6A942B7E151628AED2A6ABF7158809CF4F3C, 9A768A92F60E12D8, 890121234567890000, 504149865578056140",
            "10, EF4359D8D580AA4F7F036D6F04FC6A942B7E151628AED2A6ABF7158809CF4F3C, D8E7920AFA330A73, 89012123456789000000789000000, 04344343235792599165734622699",
            "10, EF4359D8D580AA4F7F036D6F04FC6A942B7E151628AED2A6ABF7158809CF4F3C, 0000000000000000, 89012123456789000000789000000, 30859239999374053872365555822",
            "26, EF4359D8D580AA4F7F036D6F04FC6A942B7E151628AED2A6ABF7158809CF4F3C, 9A768A92F60E12D8, 0123456789abcdefghi, p0b2godfja9bhb7bk38"
    })
    void test_NistFF3_test_vectors(int radix, String key, String tweak, String plainText, String cipherText) throws Exception {
        FF3Cipher c = new FF3Cipher(key, tweak, radix);
        assertEquals(cipherText, c.encrypt(plainText));
        assertEquals(plainText, c.decrypt(cipherText));
    }

    @ParameterizedTest
    @CsvSource({
            // AES-128 tg: 1-3 tc: 1-2  radix, alphabet, key, tweak, plaintext, ciphertext
            "10, 0123456789, 2DE79D232DF5585D68CE47882AE256D6, CBD09280979564, 3992520240, 8901801106",
            "10, 0123456789, 01C63017111438F7FC8E24EB16C71AB5, C4E822DCD09F27, 60761757463116869318437658042297305934914824457484538562, 35637144092473838892796702739628394376915177448290847293",
            "26, abcdefghijklmnopqrstuvwxyz, 718385E6542534604419E83CE387A437, B6F35084FA90E1, wfmwlrorcd, ywowehycyd",
            "26, abcdefghijklmnopqrstuvwxyz, DB602DFF22ED7E84C8D8C865A941A238, EBEFD63BCC2083, kkuomenbzqvggfbteqdyanwpmhzdmoicekiihkrm, belcfahcwwytwrckieymthabgjjfkxtxauipmjja",
            "64, 0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz+/, AEE87D0D485B3AFD12BD1E0B9D03D50D, 5F9140601D224B, ixvuuIHr0e, GR90R1q838",
            "64, 0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz+/, 7B6C88324732F7F4AD435DA9AD77F917, 3F42102C0BAB39, 21q1kbbIVSrAFtdFWzdMeIDpRqpo, cvQ/4aGUV4wRnyO3CHmgEKW5hk8H"
    })
    void test_AcvpFF3_1_test_vectors(int radix, String alphabet, String key, String tweak, String plainText, String cipherText) throws Exception {
        FF3Cipher c = (radix == 10)? new FF3Cipher(key, tweak, radix) : new FF3Cipher(key, tweak, alphabet);

        assertEquals(cipherText, c.encrypt(plainText));
        assertEquals(plainText, c.decrypt(cipherText));
    }

    @ParameterizedTest
    @CsvSource({
            "10, EF4359D8D580AA4F7F036D6F04FC6A94, D8E7920AFA330A, 890121234567890000, 477064185124354662"
    })
    void test_FF3_1_test_vectors_with_56_bit_tweak(int radix, String key, String tweak, String plainText, String cipherText) throws Exception {
        FF3Cipher c = new FF3Cipher(key, tweak, radix);

        assertEquals(cipherText, c.encrypt(plainText));
        assertEquals(plainText, c.decrypt(cipherText));
    }

    @Test
    void supports_custom_alphabet() throws Exception {
        // Check the first NIST 128-bit test vector using superscript characters
        String alphabet = "⁰¹²³⁴⁵⁶⁷⁸⁹";
        String key = "EF4359D8D580AA4F7F036D6F04FC6A94";
        String tweak = "D8E7920AFA330A73";
        String pt = "⁸⁹⁰¹²¹²³⁴⁵⁶⁷⁸⁹⁰⁰⁰⁰";
        String ct = "⁷⁵⁰⁹¹⁸⁸¹⁴⁰⁵⁸⁶⁵⁴⁶⁰⁷";
        FF3Cipher c = new FF3Cipher(key, tweak, alphabet);

        String ciphertext = c.encrypt(pt);
        assertEquals(ct, ciphertext) ;
        String plaintext = c.decrypt(ciphertext);
        assertEquals(pt, plaintext);
    }
}