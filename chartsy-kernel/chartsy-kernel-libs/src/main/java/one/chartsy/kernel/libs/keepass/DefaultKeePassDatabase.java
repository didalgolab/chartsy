package one.chartsy.kernel.libs.keepass;

import org.linguafranca.pwdb.Credentials;
import org.linguafranca.pwdb.Database;
import org.linguafranca.pwdb.Entry;
import org.linguafranca.pwdb.Group;
import org.linguafranca.pwdb.StreamConfiguration;
import org.linguafranca.pwdb.StreamFormat;
import org.linguafranca.pwdb.Visitor;
import org.openide.util.lookup.ServiceProvider;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.UUID;

@ServiceProvider(service = KeePassDatabase.class)
public class DefaultKeePassDatabase implements KeePassDatabase {

    private final Database<?, ?, ?, ?> proxy;

    public DefaultKeePassDatabase() throws IOException {
        this(DefaultKeePassDatabaseLoader.getKeePassDatabase());
    }

    protected DefaultKeePassDatabase(Database<?, ?, ?, ?> proxy) {
        this.proxy = proxy;
    }

    @Override
    public KeePassGroup getRootGroup() {
        return KeePassGroup.of(this, proxy.getRootGroup());
    }

    @Override
    public KeePassGroup newGroup() {
        return KeePassGroup.of(this, proxy.newGroup());
    }

    @Override
    public KeePassGroup newGroup(String name) {
        return KeePassGroup.of(this, proxy.newGroup(name));
    }

    @Override
    public KeePassGroup newGroup(Group<?, ?, ?, ?> group) {
        return KeePassGroup.of(this, proxy.newGroup(group));
    }

    @Override
    public KeePassEntry newEntry() {
        return KeePassEntry.of(this, proxy.newEntry());
    }

    @Override
    public KeePassEntry newEntry(String title) {
        return KeePassEntry.of(this, proxy.newEntry(title));
    }

    @Override
    public KeePassEntry newEntry(Entry<?, ?, ?, ?> entry) {
        return KeePassEntry.of(this, proxy.newEntry(entry));
    }

    @Override
    public KeePassIcon newIcon() {
        return KeePassIcon.of(proxy.newIcon());
    }

    @Override
    public KeePassIcon newIcon(Integer i) {
        return KeePassIcon.of(proxy.newIcon(i));
    }

    @Override
    public KeePassEntry findEntry(UUID uuid) {
        var found = proxy.findEntry(uuid);
        return found == null ? null : KeePassEntry.of(this, found);
    }

    @Override
    public boolean deleteEntry(UUID uuid) {
        return proxy.deleteEntry(uuid);
    }

    @Override
    public KeePassGroup findGroup(UUID uuid) {
        var found = proxy.findGroup(uuid);
        return found == null ? null : KeePassGroup.of(this, found);
    }

    @Override
    public boolean deleteGroup(UUID uuid) {
        return proxy.deleteGroup(uuid);
    }

    @Override
    public boolean isRecycleBinEnabled() {
        return proxy.isRecycleBinEnabled();
    }

    @Override
    public void enableRecycleBin(boolean enable) {
        proxy.enableRecycleBin(enable);
    }

    @Override
    public KeePassGroup getRecycleBin() {
        var bin = proxy.getRecycleBin();
        return bin == null ? null : KeePassGroup.of(this, bin);
    }

    @Override
    public void emptyRecycleBin() {
        proxy.emptyRecycleBin();
    }

    @Override
    public void visit(Visitor visitor) {
        proxy.visit(visitor);
    }

    @Override
    public void visit(KeePassGroup group, Visitor visitor) {
        // We know at runtime that it’s safe, but the compiler doesn’t.
        @SuppressWarnings("unchecked")
        Database<KeePassDatabase, KeePassGroup, KeePassEntry, KeePassIcon> typedProxy =
                (Database<KeePassDatabase, KeePassGroup, KeePassEntry, KeePassIcon>) proxy;
        typedProxy.visit(group, visitor);
    }

    @Override
    public List<? extends KeePassEntry> findEntries(Entry.Matcher matcher) {
        return proxy.findEntries(matcher).stream()
                .map(entry -> KeePassEntry.of(this, entry))
                .toList();
    }

    @Override
    public List<? extends KeePassEntry> findEntries(String find) {
        return proxy.findEntries(find).stream()
                .map(entry -> KeePassEntry.of(this, entry))
                .toList();
    }

    @Override
    public String getName() {
        return proxy.getName();
    }

    @Override
    public void setName(String name) {
        proxy.setName(name);
    }

    @Override
    public String getDescription() {
        return proxy.getDescription();
    }

    @Override
    public void setDescription(String description) {
        proxy.setDescription(description);
    }

    @Override
    public boolean isDirty() {
        return proxy.isDirty();
    }

    @Override
    public void save(Credentials credentials, OutputStream outputStream) throws IOException {
        proxy.save(credentials, outputStream);
    }

    @Override
    public <C extends StreamConfiguration> void save(StreamFormat<C> streamFormat,
                                                     Credentials credentials,
                                                     OutputStream outputStream) throws IOException {
        proxy.save(streamFormat, credentials, outputStream);
    }

    @Override
    public <C extends StreamConfiguration> StreamFormat<C> getStreamFormat() {
        return proxy.getStreamFormat();
    }

    @Override
    public boolean shouldProtect(String propertyName) {
        return proxy.shouldProtect(propertyName);
    }

    @Override
    public boolean supportsNonStandardPropertyNames() {
        return proxy.supportsNonStandardPropertyNames();
    }

    @Override
    public boolean supportsBinaryProperties() {
        return proxy.supportsBinaryProperties();
    }

    @Override
    public boolean supportsRecycleBin() {
        return proxy.supportsRecycleBin();
    }
}
