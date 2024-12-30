package one.chartsy.kernel.libs.keepass;

import org.linguafranca.pwdb.Icon;

public interface KeePassIcon extends Icon {

    /**
     * Wraps a generic Icon instance in a KeePassIcon facade.
     */
    static KeePassIcon of(Icon icon) {
        if (icon instanceof KeePassIcon keePassIcon) {
            return keePassIcon;
        }

        return new KeePassIcon() {
            @Override
            public int getIndex() {
                return icon.getIndex();
            }

            @Override
            public void setIndex(int index) {
                icon.setIndex(index);
            }
        };
    }
}
