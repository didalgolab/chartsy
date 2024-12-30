package one.chartsy.kernel.libs.keepass;

import org.linguafranca.pwdb.Database;
import org.linguafranca.pwdb.Entry;
import org.linguafranca.pwdb.Group;
import org.linguafranca.pwdb.Icon;

import java.util.List;
import java.util.UUID;

public interface KeePassGroup extends Group<KeePassDatabase, KeePassGroup, KeePassEntry, KeePassIcon> {

    /**
     * Wraps a generic Group<?, ?, ?, ?> instance in a KeePassGroup facade.
     */
    static KeePassGroup of(KeePassDatabase database, Group<?, ?, ?, ?> group) {
        if (group instanceof KeePassGroup keePassGroup) {
            return keePassGroup;
        }

        return new KeePassGroup() {
            @Override
            public boolean isRootGroup() {
                return group.isRootGroup();
            }

            @Override
            public boolean isRecycleBin() {
                return group.isRecycleBin();
            }

            @Override
            public KeePassGroup getParent() {
                var parent = group.getParent();
                return parent == null ? null : KeePassGroup.of(database, parent);
            }

            @Override
            public void setParent(KeePassGroup parent) {
                @SuppressWarnings("unchecked")
                var typedGroup = (Group<KeePassDatabase, KeePassGroup, KeePassEntry, KeePassIcon>) group;
                typedGroup.setParent(parent);
            }

            @Override
            public List<? extends KeePassGroup> getGroups() {
                return group.getGroups().stream()
                        .map(grp -> KeePassGroup.of(database, grp))
                        .toList();
            }

            @Override
            public int getGroupsCount() {
                return group.getGroupsCount();
            }

            @Override
            public KeePassGroup addGroup(KeePassGroup newGroup) {
                @SuppressWarnings("unchecked")
                var typedGroup = (Group<KeePassDatabase, KeePassGroup, KeePassEntry, KeePassIcon>) group;
                var added = typedGroup.addGroup(newGroup);
                return added == null ? null : KeePassGroup.of(database, added);
            }

            @Override
            public List<? extends KeePassGroup> findGroups(String groupName) {
                return group.findGroups(groupName).stream()
                        .map(grp -> KeePassGroup.of(database, grp))
                        .toList();
            }

            @Override
            public KeePassGroup removeGroup(KeePassGroup g) {
                @SuppressWarnings("unchecked")
                var typedGroup = (Group<KeePassDatabase, KeePassGroup, KeePassEntry, KeePassIcon>) group;
                var removed = typedGroup.removeGroup(g);
                return removed == null ? null : KeePassGroup.of(database, removed);
            }

            @Override
            public List<? extends KeePassEntry> getEntries() {
                return group.getEntries().stream()
                        .map(entry -> KeePassEntry.of(database, entry))
                        .toList();
            }

            @Override
            public int getEntriesCount() {
                return group.getEntriesCount();
            }

            @Override
            public List<? extends KeePassEntry> findEntries(String match, boolean recursive) {
                return group.findEntries(match, recursive).stream()
                        .map(entry -> KeePassEntry.of(database, entry))
                        .toList();
            }

            @Override
            public List<? extends KeePassEntry> findEntries(Entry.Matcher matcher, boolean recursive) {
                return group.findEntries(matcher, recursive).stream()
                        .map(entry -> KeePassEntry.of(database, entry))
                        .toList();
            }

            @Override
            public KeePassEntry addEntry(KeePassEntry entry) {
                @SuppressWarnings("unchecked")
                var typedGroup = (Group<KeePassDatabase, KeePassGroup, KeePassEntry, KeePassIcon>) group;
                var added = typedGroup.addEntry(entry);
                return added == null ? null : KeePassEntry.of(database, added);
            }

            @Override
            public KeePassEntry removeEntry(KeePassEntry entry) {
                @SuppressWarnings("unchecked")
                var typedGroup = (Group<KeePassDatabase, KeePassGroup, KeePassEntry, KeePassIcon>) group;
                var removed = typedGroup.removeEntry(entry);
                return removed == null ? null : KeePassEntry.of(database, removed);
            }

            @Override
            public void copy(Group<? extends Database, ? extends Group, ? extends Entry, ? extends Icon> parent) {
                group.copy(parent);
            }

            @Override
            public String getPath() {
                return group.getPath();
            }

            @Override
            public String getName() {
                return group.getName();
            }

            @Override
            public void setName(String name) {
                group.setName(name);
            }

            @Override
            public UUID getUuid() {
                return group.getUuid();
            }

            @Override
            public KeePassIcon getIcon() {
                var icon = group.getIcon();
                return icon == null ? null : KeePassIcon.of(icon);
            }

            @Override
            public void setIcon(KeePassIcon icon) {
                @SuppressWarnings("unchecked")
                var typedGroup = (Group<KeePassDatabase, KeePassGroup, KeePassEntry, KeePassIcon>) group;
                typedGroup.setIcon(icon);
            }

            @Override
            public KeePassDatabase getDatabase() {
                return database;
            }
        };
    }
}
