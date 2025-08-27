/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data.structures;

import java.util.*;

/**
 * SupersetIndex is a data structure for storing many {@code Set<String>} values and
 * querying all stored sets that are supersets (or equal) of a given query set.
 *
 * <p>Core idea:
 * <ul>
 * <li>Map each element string to an integer {@code itemId}.</li>
 * <li>Maintain an inverted index: for each {@code itemId}, a posting list of {@code setIds} that contain it.</li>
 * <li>On query for a {@code Set<String>} Q, we count occurrences of setIds across posting lists of Q's items.
 *    Any setId that appears exactly |Q| times corresponds to a stored set that contains all items of Q.</li>
 * </ul>
 *
 * <p>Complexity (n = number of stored sets, m = number of distinct items, f = total memberships):
 * <ul>
 * <li>add(S): O(|S| log |S|) due to per-set sorting/dedup during ingestion; posting list updates are O(|S|).</li>
 * <li>supersetsOf(Q): O(sum of postingList sizes for items in Q), with tiny overheads.</li>
 * <li>Memory: O(m + n + f). Each membership is recorded once in the set's compact int[] and once in the inverted index.</li>
 * </ul>
 *
 * <p>Notes:
 * <ul>
 * <li>Thread-safety: not synchronized.</li>
 * <li>Null elements are rejected.</li>
 * <li>An empty query returns all stored sets (every set is a superset of the empty set).</li>
 * <li>By default, equal sets are deduplicated (can be disabled).</li>
 * </ul>
 *
 * @author Mariusz Bernacki
 */
public final class SupersetIndex {

    // Map from element string to itemId (dense 0..m-1)
    private final Map<String, Integer> itemToId = new HashMap<>();
    // Reverse map (itemId -> string)
    private final List<String> idToItem = new ArrayList<>();
    // Inverted index: for each itemId, a posting list of setIds that contain it.
    private final List<IntList> postings = new ArrayList<>();
    // Stored sets: each setId maps to a sorted, duplicate-free array of itemIds.
    private final List<int[]> sets = new ArrayList<>();

    // Optional canonicalization to deduplicate identical sets.
    private final Map<IntArrayKey, Integer> canonical;
    private final boolean deduplicate;

    // Scratch space reused across queries: O(number of sets)
    private int[] scratchCounts = new int[0];
    private int[] scratchStamp = new int[0];
    private int scratchVersion = 1; // grows; lazily resets on overflow
    private final IntList scratchTouched = new IntList(16);

    public SupersetIndex() {
        this(true);
    }

    public SupersetIndex(boolean deduplicate) {
        this.deduplicate = deduplicate;
        this.canonical = deduplicate ? new HashMap<>() : null;
    }

    /**
     * Adds a set of strings to the index. Returns the setId of the stored (or existing) set.
     * If deduplication is enabled and an equal set was already present, returns its existing id.
     */
    public int add(Set<String> set) {
        if (set == null) throw new NullPointerException("set");
        // Special-case: empty set
        if (set.isEmpty()) {
            int[] empty = new int[0];
            if (deduplicate) {
                IntArrayKey key = new IntArrayKey(empty);
                Integer ex = canonical.get(key);
                if (ex != null) return ex;
                int id = sets.size();
                sets.add(empty);
                canonical.put(key, id);
                ensureScratchCapacity(sets.size());
                return id;
            } else {
                int id = sets.size();
                sets.add(empty);
                ensureScratchCapacity(sets.size());
                return id;
            }
        }

        // Map elements to itemIds
        int[] ids = new int[set.size()];
        int k = 0;
        for (String s : set) {
            if (s == null) throw new IllegalArgumentException("Null element in set");
            int itemId = getOrCreateItemId(s);
            ids[k++] = itemId;
        }

        // Canonicalize per-set: sort and deduplicate itemIds
        Arrays.sort(ids, 0, k);
        int uniqueCount = dedupSortedInPlace(ids, k);
        int[] unique = Arrays.copyOf(ids, uniqueCount);

        // Dedup identical sets if requested
        if (deduplicate) {
            IntArrayKey key = new IntArrayKey(unique);
            Integer existing = canonical.get(key);
            if (existing != null) return existing;
            int setId = sets.size();
            sets.add(unique);
            canonical.put(key, setId);
            // Append setId to each item's posting list (monotonic increasing setId keeps postings sorted)
            for (int itemId : unique) {
                postings.get(itemId).add(setId);
            }
            ensureScratchCapacity(sets.size());
            return setId;
        } else {
            int setId = sets.size();
            sets.add(unique);
            for (int itemId : unique) {
                postings.get(itemId).add(setId);
            }
            ensureScratchCapacity(sets.size());
            return setId;
        }
    }

    /**
     * Returns all stored sets that are supersets of the given query.
     * The returned sets are unmodifiable snapshots materialized as Sets of Strings.
     * If the query is empty, returns all stored sets.
     */
    public List<Set<String>> supersetsOf(Set<String> query) {
        List<Integer> ids = supersetIdsOf(query);
        List<Set<String>> out = new ArrayList<>(ids.size());
        for (int setId : ids) {
            out.add(materialize(setId));
        }
        return out;
    }

    /**
     * Returns the setIds of all stored sets that are supersets of the given query.
     * If the query is empty, all setIds are returned.
     */
    public List<Integer> supersetIdsOf(Set<String> query) {
        if (query == null) throw new NullPointerException("query");

        // Empty query -> everything qualifies.
        if (query.isEmpty()) {
            List<Integer> out = new ArrayList<>(sets.size());
            for (int i = 0; i < sets.size(); i++) out.add(i);
            return out;
        }

        // Map query strings to itemIds; early exit if any item is unknown.
        int[] qIds = new int[query.size()];
        int qk = 0;
        for (String s : query) {
            if (s == null) throw new IllegalArgumentException("Null element in query");
            int id = getItemIdOrNegOne(s);
            if (id == -1) return Collections.emptyList();
            qIds[qk++] = id;
        }

        // Deduplicate query itemIds
        Arrays.sort(qIds, 0, qk);
        int qDistinct = dedupSortedInPlace(qIds, qk);
        if (qDistinct == 0) return Collections.emptyList();

        // Reuse scratch arrays sized to number of sets
        ensureScratchCapacity(sets.size());
        int stamp = ++scratchVersion;
        if (stamp == 0) { // extremely unlikely; handle int overflow by resetting stamps
            Arrays.fill(scratchStamp, 0);
            scratchVersion = 1;
            stamp = 1;
        }
        scratchTouched.clear();

        // Count occurrences of setIds across posting lists of query items.
        for (int i = 0; i < qDistinct; i++) {
            IntList plist = postings.get(qIds[i]);
            int n = plist.size();
            for (int j = 0; j < n; j++) {
                int setId = plist.get(j);
                if (scratchStamp[setId] != stamp) {
                    scratchStamp[setId] = stamp;
                    scratchCounts[setId] = 1;
                    scratchTouched.add(setId);
                } else {
                    scratchCounts[setId]++;
                }
            }
        }

        // Collect setIds that appeared in all posting lists (i.e., contain all query items)
        List<Integer> out = new ArrayList<>();
        for (int i = 0, n = scratchTouched.size(); i < n; i++) {
            int setId = scratchTouched.get(i);
            if (scratchCounts[setId] == qDistinct) out.add(setId);
        }
        return out;
    }

    /** Number of stored sets (after deduplication if enabled). */
    public int numSets() {
        return sets.size();
    }

    /** Number of distinct element strings seen. */
    public int numDistinctItems() {
        return idToItem.size();
    }

    /** Returns an unmodifiable Set<String> view of a stored set by id. */
    public Set<String> getSetById(int setId) {
        if (setId < 0 || setId >= sets.size()) throw new IndexOutOfBoundsException();
        return materialize(setId);
    }

    // ----- Internal helpers -----

    private int getOrCreateItemId(String s) {
        Integer id = itemToId.get(s);
        if (id != null) return id;
        int newId = idToItem.size();
        itemToId.put(s, newId);
        idToItem.add(s);
        postings.add(new IntList());
        return newId;
    }

    private int getItemIdOrNegOne(String s) {
        Integer id = itemToId.get(s);
        return id == null ? -1 : id;
    }

    private Set<String> materialize(int setId) {
        int[] items = sets.get(setId);
        Set<String> out = new LinkedHashSet<>(items.length * 2);
        for (int itemId : items) {
            out.add(idToItem.get(itemId));
        }
        return Collections.unmodifiableSet(out);
    }

    private static int dedupSortedInPlace(int[] a, int len) {
        if (len == 0) return 0;
        int w = 1;
        for (int r = 1; r < len; r++) {
            if (a[r] != a[w - 1]) {
                a[w++] = a[r];
            }
        }
        return w;
    }

    private void ensureScratchCapacity(int nSets) {
        if (scratchCounts.length < nSets) {
            scratchCounts = Arrays.copyOf(scratchCounts, nSets);
            scratchStamp = Arrays.copyOf(scratchStamp, nSets);
        }
    }

    // Compact dynamic int list used for posting lists and scratch storage.
    private static final class IntList {
        private int[] data;
        private int size;

        IntList() { this(8); }

        IntList(int initialCapacity) {
            if (initialCapacity < 0) throw new IllegalArgumentException("capacity < 0");
            this.data = new int[Math.max(1, initialCapacity)];
            this.size = 0;
        }

        void add(int value) {
            if (size == data.length) {
                data = Arrays.copyOf(data, data.length + (data.length >>> 1) + 1);
            }
            data[size++] = value;
        }

        int get(int index) {
            if (index < 0 || index >= size) throw new IndexOutOfBoundsException();
            return data[index];
        }

        int size() { return size; }

        void clear() { size = 0; }
    }

    // Hashable key wrapping an int[] by content.
    private static final class IntArrayKey {
        private final int[] a;
        private final int hash;

        IntArrayKey(int[] a) {
            this.a = a;
            this.hash = Arrays.hashCode(a);
        }

        @Override public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof IntArrayKey that))
                return false;
            return Arrays.equals(this.a, that.a);
        }

        @Override public int hashCode() { return hash; }
    }

    // Minimal demo
    public static void main(String[] args) {
        SupersetIndex index = new SupersetIndex(true);

        index.add(Set.of("a", "b"));
        index.add(Set.of("a", "c"));
        index.add(Set.of("a", "b", "c"));
        index.add(Set.of("d"));
        index.add(Set.of("b", "c", "e"));

        System.out.println("Supersets of {a}:");
        for (Set<String> s : index.supersetsOf(Set.of("a"))) {
            System.out.println(s);
        }

        System.out.println("Supersets of {b, c}:");
        for (Set<String> s : index.supersetsOf(Set.of("b", "c"))) {
            System.out.println(s);
        }

        System.out.println("Supersets of {}: " + index.supersetsOf(Collections.emptySet()).size());
    }
}
