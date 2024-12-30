package one.chartsy.kernel.libs.keepass;

import org.linguafranca.pwdb.Entry;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public interface KeePassEntry extends Entry<KeePassDatabase, KeePassGroup, KeePassEntry, KeePassIcon> {

    /**
     * Wraps a generic Entry<?, ?, ?, ?> instance in a KeePassEntry facade.
     */
    static KeePassEntry of(KeePassDatabase database, Entry<?, ?, ?, ?> entry) {
        if (entry instanceof KeePassEntry keePassEntry) {
            return keePassEntry;
        }

        return new KeePassEntry() {
            @Override
            public boolean match(String text) {
                return entry.match(text);
            }

            @Override
            public boolean match(Matcher matcher) {
                return entry.match(matcher);
            }

            @Override
            public String getPath() {
                return entry.getPath();
            }

            @Override
            public String getProperty(String name) {
                return entry.getProperty(name);
            }

            @Override
            public void setProperty(String name, String value) {
                entry.setProperty(name, value);
            }

            @Override
            public boolean removeProperty(String name) throws IllegalArgumentException, UnsupportedOperationException {
                return entry.removeProperty(name);
            }

            @Override
            public List<String> getPropertyNames() {
                return entry.getPropertyNames();
            }

            @Override
            public byte[] getBinaryProperty(String name) {
                return entry.getBinaryProperty(name);
            }

            @Override
            public void setBinaryProperty(String name, byte[] value) {
                entry.setBinaryProperty(name, value);
            }

            @Override
            public boolean removeBinaryProperty(String name) throws UnsupportedOperationException {
                return entry.removeBinaryProperty(name);
            }

            @Override
            public List<String> getBinaryPropertyNames() {
                return entry.getBinaryPropertyNames();
            }

            @Override
            public KeePassGroup getParent() {
                // Wrap the parent group if it exists
                var parent = entry.getParent();
                return parent == null ? null : KeePassGroup.of(database, parent);
            }

            @Override
            public UUID getUuid() {
                return entry.getUuid();
            }

            @Override
            public String getUsername() {
                return entry.getUsername();
            }

            @Override
            public void setUsername(String username) {
                entry.setUsername(username);
            }

            @Override
            public boolean matchUsername(String username) {
                return entry.matchUsername(username);
            }

            @Override
            public String getPassword() {
                return entry.getPassword();
            }

            @Override
            public void setPassword(String pass) {
                entry.setPassword(pass);
            }

            @Override
            public String getUrl() {
                return entry.getUrl();
            }

            @Override
            public void setUrl(String url) {
                entry.setUrl(url);
            }

            @Override
            public boolean matchUrl(String url) {
                return entry.matchUrl(url);
            }

            @Override
            public String getTitle() {
                return entry.getTitle();
            }

            @Override
            public void setTitle(String title) {
                entry.setTitle(title);
            }

            @Override
            public boolean matchTitle(String text) {
                return entry.matchTitle(text);
            }

            @Override
            public String getNotes() {
                return entry.getNotes();
            }

            @Override
            public void setNotes(String notes) {
                entry.setNotes(notes);
            }

            @Override
            public boolean matchNotes(String text) {
                return entry.matchNotes(text);
            }

            @Override
            public KeePassIcon getIcon() {
                var icon = entry.getIcon();
                return icon == null ? null : KeePassIcon.of(icon);
            }

            @Override
            public void setIcon(KeePassIcon icon) {
                @SuppressWarnings("unchecked")
                var typedEntry = (Entry<KeePassDatabase, KeePassGroup, KeePassEntry, KeePassIcon>) entry;
                typedEntry.setIcon(icon);
            }

            @Override
            public Date getLastAccessTime() {
                return entry.getLastAccessTime();
            }

            @Override
            public Date getCreationTime() {
                return entry.getCreationTime();
            }

            @Override
            public boolean getExpires() {
                return entry.getExpires();
            }

            @Override
            public void setExpires(boolean expires) {
                entry.setExpires(expires);
            }

            @Override
            public Date getExpiryTime() {
                return entry.getExpiryTime();
            }

            @Override
            public void setExpiryTime(Date expiryTime) throws IllegalArgumentException {
                entry.setExpiryTime(expiryTime);
            }

            @Override
            public Date getLastModificationTime() {
                return entry.getLastModificationTime();
            }
        };
    }
}
