package ulid4j;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author shamil
 */
class UlidTest {
    private static final int LOOP_SIZE = 1000000;

    @Test
    public void testUlidNext() {
        Ulid ulid = new Ulid();
        String[] generated = new String[LOOP_SIZE];
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < LOOP_SIZE; i++) {
            generated[i] = ulid.next();
        }

        long endTime = System.currentTimeMillis();

        checkNullOrInvalid(generated);
        checkUniqueness(generated);
        checkCreationTime(generated, startTime, endTime);
    }

    private void checkNullOrInvalid(String[] generatedUlids) {
        for (String id : generatedUlids) {
            assertNotNull(id);
            assertFalse(id.isEmpty());
            assertEquals(26, id.length());
            assertTrue(Ulid.isValid(id));
        }
    }

    private void checkUniqueness(String[] list) {
        HashSet<String> set = new HashSet<>(list.length);
        for (String ulid : list) {
            assertTrue(set.add(ulid));
        }

        assertEquals(set.size(), list.length);
    }

    private void checkCreationTime(String[] list, long startTime, long endTime) {
        assertTrue(startTime <= endTime);

        for (String ulid : list) {
            long creationTime = Ulid.unixTime(ulid);
            assertTrue(creationTime >= startTime);
            assertTrue(creationTime <= endTime);
        }
    }
}