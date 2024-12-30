package one.chartsy.kernel.libs.keepass;

import org.linguafranca.pwdb.Database;
import org.openide.util.Lookup;

public interface KeePassDatabase extends Database<KeePassDatabase, KeePassGroup, KeePassEntry, KeePassIcon> {

    static KeePassDatabase getDefault() {
        return Lookup.getDefault().lookup(KeePassDatabase.class);
    }
}
