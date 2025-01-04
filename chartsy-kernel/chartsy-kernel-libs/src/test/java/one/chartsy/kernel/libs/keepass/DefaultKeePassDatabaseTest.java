package one.chartsy.kernel.libs.keepass;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DefaultKeePassDatabaseTest {

    @Disabled
    @Test
    void getDefault() {
        assertNotNull(KeePassDatabase.getDefault());
    }
}