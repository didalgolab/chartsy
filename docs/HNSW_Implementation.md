Below is a **reworked, top‑down design and implementation plan** for a production‑grade **HNSW in Java** with **online insert**, **online remove + repair**, **KNN search**, and **pluggable distance functions**. It is structured as a sequence of **milestones** you can implement and validate in order. Each milestone has deliverables and acceptance checks so a developer can start immediately.

---

## 0) Overview

**Goals**

* High‑performance approximate KNN using HNSW
* Online **add** and **remove** (with lazy delete + local repair)
* Solid invariants and testability (deterministic under fixed seed)
* Pluggable **distance functions** (e.g., Euclidean, Cosine, Correlation, custom)
* Efficient memory layout and low GC pressure
* Clear path to thread‑safe reads and serialized persistence

**Non‑goals (for v1)**

* Distributed sharding (single‑process index only)
* Arbitrary object payloads (store external `long id` + vector)
* Perfect recall; we optimize for quality/speed trade‑off

**Key HNSW parameters (with typical defaults)**

* `M=16` (max neighbors per level >0)
* `maxM0=32` (max neighbors at level 0)
* `efConstruction=200` (beam width during insertion)
* `efSearch=50` (beam width during search)
* Level assignment via geometric distribution with `levelLambda=1.0`

---

## 1) Public API (stable surface)

```java
public interface HnswIndex {
    // Build / mutate
    void add(long id, float[] vector);           // upsert by default (configurable policy)
    boolean remove(long id);                     // true if removed (lazy delete + repair)
    boolean contains(long id);

    // Query
    List<SearchResult> searchKnn(float[] query, int k);            // uses default efSearch
    List<SearchResult> searchKnn(float[] query, int k, int efSearch);

    // Info
    int size();                                   // non-deleted
    int dimension();
    HnswStats stats();                            // degrees, levels, edges, memory estimates

    // Lifecycle
    void clear();
    void save(Path path) throws IOException;      // optional milestone
    static HnswIndex load(Path path) { ... }      // optional milestone
}
```

```java
public record SearchResult(long id, float distance) {}
```

### Configuration (builder)

```java
public final class HnswConfig {
    public int dimension;
    public SpaceFactory spaceFactory;     // pluggable distance space (see §2)
    public int M = 16;
    public int maxM0 = 32;
    public int efConstruction = 200;
    public int defaultEfSearch = 50;
    public float levelLambda = 1.0f;
    public long randomSeed = 42L;

    public DuplicatePolicy duplicatePolicy = DuplicatePolicy.UPSERT; // or REJECT
    public DeletionPolicy deletionPolicy = DeletionPolicy.LAZY_WITH_REPAIR; // or LAZY_ONLY
    public NeighborSelectHeuristic neighborHeuristic = NeighborSelectHeuristic.DIVERSIFIED; // or SIMPLE

    public int initialCapacity = 16_384;
}
```

```java
public final class Hnsw {
    public static HnswIndex build(HnswConfig config) { ... }
}
```

---

## 2) Distance functions: pluggable and fast

### 2.1 Concepts

* **Space**: encapsulates a distance definition and any per‑vector/per‑query precomputation needed for speed.
* **QueryContext**: per‑query scratch to avoid recomputing norms/means repeatedly.
* Contract: **distance is “lower is better”** (similarities must be converted).

```java
public interface Space {
    int dimension();
    // Optionally called during add(), to precompute/stash per-node aux data (e.g., norm or mean)
    void onInsert(int nodeId, float[] vector, AuxVectorData aux);
    QueryContext prepareQuery(float[] query);    // may compute query norm/mean/etc.
    float distance(QueryContext q, int nodeId, VectorStorage vs, AuxStorage aux);
}

public interface QueryContext { /* marker for per-query state */ }

public interface SpaceFactory { Space create(HnswConfig cfg, VectorStorage vs, AuxStorage aux); }
```

### 2.2 Provided spaces

* **EuclideanSpace**: plain L2 distance, no aux data.

* **CosineSpace**: either:

    * Normalize vectors on insert and use inner product, **or**
    * Store `||v||` in aux and compute `1 - dot(q, v) / (||q|| · ||v||)`.

* **CorrelationSpace**: `1 - ρ(x,y)`. Options:

    * **Recommended**: mean‑center offline and then use CosineSpace on centered vectors.
    * **Direct**: store per‑vector `(mean, centeredNorm)` in aux; per query compute `(mean, centeredNorm)`, then distance uses centered dot product. (Heavier but works.)

* **CustomSpace**: wrap a user‑supplied `(float[] a, float[] b) -> float` function. (For performance, encourage users to pre‑normalize outside.)

**Aux data plumbing**

```java
public final class AuxVectorData {
    float norm;        // for cosine/inner product
    float mean;        // for correlation
    float centeredNorm;
    // extend as needed
}
```

---

## 3) Core data structures

### 3.1 IDs and bookkeeping

* Internal node ids: `int nodeId` in `[0..capacity)`.
* External ids: `long`.
* Maps:

    * `Long2IntOpenHashMap idToInternal` (fastutil)
    * `long[] internalToId`
* Deletion flags: `BitSet deleted`
* Reuse pool: `IntArrayStack freeList`

### 3.2 Vector storage (flat & cache‑friendly)

```
float[] vectors;         // length = capacity * dim, row-major
int dim;

inline offset(nodeId) = nodeId * dim
```

`VectorStorage` offers:

* `float dot(int nodeId, float[] q)`
* `float l2(int nodeId, float[] q)`
* Bulk operations avoiding per‑call allocations.

### 3.3 Graph storage

* `int[] levelOfNode` (highest level for nodeId)
* `int entryPoint = -1`
* `int maxLevel = -1`

Per level:

* `NeighborList[] layers[level]` where `NeighborList` is fixed‑capacity:

  ```java
  final class NeighborList {
      final int[] ids;  // length = capacityForLevel(level)
      int size;
      boolean addIfAbsent(int v);
      void removeIfPresent(int v);
      int capacity();
  }
  ```

    * Level 0 capacity = `maxM0`, else `M`.
    * Keep unsorted; only selection routines sort views when needed.

**Visited marking** (hot path):

* `int[] visitMark` and a rolling `int visitToken`:

    * visited if `visitMark[nodeId] == visitToken`; else set to `visitToken`.
    * When `visitToken` nears overflow, bulk clear.

**Scratch** (avoid GC):

* Per‑thread `SearchScratch` with:

    * int priority queues backed by arrays (binary heaps)
    * candidate set, result set
    * temporary arrays for neighbor selection

---

## 4) Algorithms

### 4.1 Search (EF‑search)

**Top levels (greedy):**

1. Start at `entryPoint` on `maxLevel`.
2. For each level `L = maxLevel..1`:

    * Greedy descend: move to any neighbor closer to query until local minimum.

**Level 0 (best‑first beam):**

* Maintain:

    * **Candidates (min‑heap by distance)**
    * **Results (max‑heap bounded to efSearch)**
* While candidates not empty:

    * Pop `c`. If `distance(c) > worst(results)` and `results.size >= efSearch`, break.
    * For each neighbor `n` at level 0:

        * If not visited and not deleted:

            * Compute `d`. If `results.size < efSearch` or `d < worst(results)`: push into both heaps.

Return the best **k** from `results` (filter deleted again just in case; if less than k remain, return what you have).

**Corner cases**

* Empty index → return `[]`
* `efSearch < k` → treat as `efSearch = k`

### 4.2 Insert

1. Assign level `L` ~ geometric(`levelLambda`).

2. If empty: set `entryPoint = node`, `maxLevel = L`, connect nothing, return.

3. Let `ep = entryPoint`.

4. For levels `maxLevel..(L+1)`: greedy descend from `ep` to get closer `ep`.

5. For levels `min(L, maxLevel)..0`:

    * Run EF‑search with `efConstruction` starting at `ep` on that level → candidate set `C`.
    * **Neighbor selection** (see below) to pick up to `M` (or `maxM0` at level 0) neighbors `S`.
    * **Connect**:

        * For each `e ∈ S`: bidirectional connect `(node, e)` at level `L` obeying capacity and selection heuristic on `e` if full.
    * Set `ep` to the closest node from `S` (improves locality for next lower level).

6. If `L > maxLevel`: set `entryPoint = node`, `maxLevel = L`.

**Neighbor selection heuristic (diversified)**

* Sort candidates by distance ascending to the **new node**.
* Greedily add a candidate `p` if it is not “occluded” by already selected neighbors:

    * i.e., for all `s ∈ selected`, `dist(p, s) > dist(p, node)` (or `dist(p, s) > α·dist(p, node)` with `α≥1`).
* If you run out of slots, stop when `selected.size == maxDegree(level)`.
* If `NeighborSelectHeuristic.SIMPLE`: take first `maxDegree` by distance.

**When neighbor list of `e` is full**:

* Temporarily treat `e.neighbors + node` as pool, rerun **e’s** local selection to keep best `M` w.r.t. **e**.

### 4.3 Remove (lazy + repair)

**Lazy deletion (always)**

1. Lookup nodeId; if missing or already deleted → return false.
2. `deleted.set(nodeId) = true; idToInternal.remove(id); size--`
3. If `entryPoint == nodeId`: choose a new entry as the non‑deleted node with the highest level (scan).
4. **Do not** reuse `nodeId` yet (prevents races if you add concurrency later).

**Local repair (recommended)**

For each level `L ≤ levelOfNode[nodeId]`:

* Let `N = copy of neighbors(L, nodeId)`.

* For each `u in N`: remove back edge `(u -> nodeId)`.

* **Reconnect neighbors**:

    * For all pairs `(u, v)` in `N` with both not deleted: `connectBidirectional(L, u, v)` (subject to capacity + selection).
    * Optional/Better: for each `u`, run a tiny EF‑search (`efRepair`, e.g., 20..40) seeded by `N` to find better alternatives than just pairwise `N`.

* Clear `nodeId`’s neighbor lists.

**Entry point sanity**

* After removing, if the component splits, HNSW still works because searches start at `entryPoint` and navigate the connected subgraph. Quality can drop if a large hub is removed; `efRepair` helps.

**Reuse slot**

* After repair completes, push `nodeId` to `freeList` and wipe its aux/vector state.

---

## 5) Invariants (assert in debug builds)

* If `deleted[i] == true` then:

    * `i` must not be returned by search.
    * No neighbor list may contain duplicates of `i`.
* Degree bounds hold: `deg0 ≤ maxM0`, `degL ≤ M` for `L>0`.
* `entryPoint == -1` iff `size == 0`; else `entryPoint` is not deleted.
* No self loops.
* For any neighbor list, ids are unique (use a tiny O(deg) check on insert).

---

## 6) Error handling & safety

* Validate `vector.length == dimension`.
* For `searchKnn`: `k >= 1`; clamp `efSearch = max(efSearch, k)`.
* If distance returns `NaN` or `Inf`, treat as `+∞` and log (helps catch data issues).
* Determinism: allow setting a fixed `randomSeed`.

---

## 7) Implementation plan by **milestones**

### Milestone 1 — Project skeleton & config

**Deliverables**

* `HnswIndex`, `HnswConfig`, builder.
* Data holders: `SearchResult`, `HnswStats`.
* Stubs for internal packages: `space`, `store`, `graph`, `algo`, `util`.

**Acceptance checks**

* Can create an empty index with chosen `dimension` and `SpaceFactory`.
* Unit tests compile and run.

---

### Milestone 2 — Vector & aux storage

**Implement**

* `VectorStorage` (flat `float[]`, grow by doubling).
* `AuxStorage` (parallel arrays for per‑node aux fields; lazy allocated per space needs).
* ID maps: `idToInternal`, `internalToId`, `freeList`, `deleted`.

**Acceptance**

* Add N vectors; `size()==N`; `contains(id)` works.
* Capacity grows without data loss (spot checks).

---

### Milestone 3 — Distance spaces (pluggable)

**Implement**

* `SpaceFactory` + `Space` + `QueryContext`.
* `EuclideanSpace`, `CosineSpace` (with aux `norm` or pre‑normalize), `CorrelationSpace` (either direct with `(mean, centeredNorm)` aux or document “center offline + cosine”).
* `CustomSpace` wrapper.

**Acceptance**

* Unit tests verifying distances against hand‑computed examples.
* For cosine/correlation, test invariants on normalized/centered vectors.

---

### Milestone 4 — Graph container & neighbor list

**Implement**

* `NeighborList` fixed‑capacity with `addIfAbsent`, `removeIfPresent`.
* `HnswGraph`:

    * `levelOfNode[]`, `entryPoint`, `maxLevel`, `layers[level][nodeId]`.
    * `ensureLevel(level)`, `ensureNeighborList(level, nodeId)`.

**Acceptance**

* Add/remove to `NeighborList` respects capacity and uniqueness.
* Degree bounds enforced in unit tests.

---

### Milestone 5 — **Search** (multi‑level greedy + EF at level 0)

**Implement**

* `SearchScratch` with array‑backed min/max heaps and `visitMark` scheme.
* `searchKnn(q, k, efSearch)` using the algorithm in §4.1 with `Space.prepareQuery(q)`.

**Acceptance**

* On tiny synthetic data (e.g., points on a line), answers match brute force.
* For random data (10k points, 32‑D):

    * With `efSearch=10*k`, recall ≥ 0.9 for `k=10` (ballpark; record exact value).

---

### Milestone 6 — **Insert** (HNSW build)

**Implement**

* Level sampling with geometric distribution.
* Greedy descent from `entryPoint`.
* EF‑search with `efConstruction` on each level.
* **Neighbor selection**:

    * Implement both SIMPLE and DIVERSIFIED (occlusion check with `α=1.0`).
* `connectBidirectional(level, u, v)` with capacity enforcement (= local selection on `u` when full).

**Acceptance**

* Build index from scratch on random data.
* Compare recall against brute force; plot recall vs `efSearch` sanity (record numbers).
* Validate invariants: degrees, no self‑loops, uniqueness.

---

### Milestone 7 — **Remove** (lazy + repair)

**Implement**

* `remove(id)`:

    * Lazy delete flag + map cleanup, `entryPoint` reselection if needed.
    * Repair procedure in §4.3:

        * Remove back edges from neighbors to the deleted node.
        * Pairwise reconnection among neighbors (and optionally small `efRepair` search seeded by neighbors).
        * Clear deleted node’s neighbor lists.
    * Return slot to `freeList`.

**Acceptance**

* After deleting random 10–30% of nodes:

    * No deleted ids appear in search results.
    * Recall drop is modest with repair vs. no‑repair (measure & record).
* Deleting `entryPoint` does not break searches.

---

### Milestone 8 — API hardening & updates

**Implement**

* `duplicatePolicy`: choose between REJECT and UPSERT (UPSERT = remove+add).
* `searchKnn` overload with explicit `efSearch`.
* `stats()` (levels distribution, edges, avg degree, memory).

**Acceptance**

* Duplicate add behaves per policy.
* `stats` numbers match expectations (e.g., edge count ≈ N * avgDegree / 2 at level 0 + …).

---

### Milestone 9 — Performance pass

**Implement**

* Avoid object churn in hot paths (primitive heaps, no `NodeDistance` boxing).
* SIMD‑friendly loops in `VectorStorage` (manual unroll 4/8).
* Thread‑local `SearchScratch`.
* Optional: prefetch‑like stride batching in distance loops (JMH‑driven).

**Acceptance (JMH)**

* Baseline QPS and latency for:

    * Search (50th/95th) on 1e5 vectors, 128‑D, cosine.
    * Insert throughput (nodes/sec) at `efConstruction=200`.
* Show perf uplift vs naive version (record numbers).

---

### Milestone 10 — Concurrency (read‑mostly)

**Implement (optional for v1)**

* `ReentrantReadWriteLock` at index level:

    * `searchKnn` under read lock
    * `add/remove` under write lock
* Ensure **no** per‑search shared mutable state (all scratch is thread‑local).
* Slot reuse only after write operations complete.

**Acceptance**

* Multi‑threaded search correctness under concurrent reads.
* Add/remove with concurrent reads does not throw or return deleted nodes.

---

### Milestone 11 — Serialization (optional)

**Implement**

* Binary format:

    * Header: magic, version, config, dimension, N, maxLevel
    * Arrays: `internalToId`, `deleted`, `levelOfNode`, `vectors`, `aux`, neighbor lists per level
* `save(Path)` / `load(Path)` with checksum.

**Acceptance**

* Save + load round‑trip equality (bit‑for‑bit for arrays).
* Post‑load searches match pre‑save results.

---

### Milestone 12 — Robustness & fuzzing

**Implement**

* Property tests (jqwik) that randomly:

    * Insert/remove/search, while validating:

        * No deleted id in results
        * Degree bounds
        * Determinism under fixed seed
* Long‑run fuzz (minutes) to catch edge cases (array bounds, token wrap‑around).

**Acceptance**

* Fuzz passes without assertion failures or memory leaks (track with heap dumps or YourKit).

---

## 8) Key implementation details (ready‑to‑code notes)

* **Capacity growth**: double arrays; for `layers`, grow outer references and lazily allocate `NeighborList` on first use.
* **EntryPoint reselection**: scan `levelOfNode` for the max level among non‑deleted; if none, set `entryPoint=-1`.
* **Heaps**:

    * Candidates: min‑heap on (distance, nodeId)
    * Results: max‑heap bounded to `efSearch`
* **Visited marking**: `visitToken++` per search; if overflow (negative), `Arrays.fill(visitMark, 0)` and reset token to `1`.
* **Distance safety**: clamp any `NaN` to `+∞` and skip candidate; log rate.
* **Cosine speed**: prefer **normalize on insert** and use inner product; store nothing extra; distance = `1 - dot(q, v)` with both unit‑norm.
* **Correlation**: recommend **mean‑center offline** if possible; otherwise store `(mean, centeredNorm)` per node and compute centered dot on the fly.
* **Neighbor replacement**: when `e` is full, build a temporary view `pool = e.neighbors ∪ {node}`, run local selection w.r.t. `e`, then write back the chosen ids.
* **Repair complexity**: degrees are bounded ⇒ pairwise reconnection is cheap (`O(M^2)` per level per deletion). Start with pairwise; add `efRepair` only if quality needs it.

---

## 9) Testing checklist (unit + integration)

* **Unit**

    * Distances: Euclidean, Cosine, Correlation edge cases (zeros, constant vectors).
    * NeighborList capacity/uniqueness.
    * Level sampler distribution sanity (histogram).
* **Integration**

    * Build + search vs brute force (exact KNN) on small sets; compute recall@k.
    * Delete random nodes; ensure none appear in results; measure recall drift.
    * Determinism with fixed seed (repeat build+queries).
    * Large dimension mismatch throws helpful errors.
* **Performance**

    * JMH: search latency vs `efSearch` curve; insert throughput vs `efConstruction`.

---

## 10) Example usage

```java
var cfg = new HnswConfig();
cfg.dimension = 128;
cfg.spaceFactory = Spaces.cosineNormalized(); // normalize on insert
cfg.M = 16;
cfg.maxM0 = 32;
cfg.efConstruction = 200;
cfg.defaultEfSearch = 50;

var index = Hnsw.build(cfg);

// Add
index.add(1001L, vec1);
index.add(1002L, vec2);

// Query
var results = index.searchKnn(queryVec, 10); // uses default efSearch

// Remove
index.remove(1002L);
```

For **correlation**:

```java
cfg.spaceFactory = Spaces.correlationDirect(); // or center offline + cosineNormalized()
```

---

## 11) Risks & mitigations

* **Quality drop after many deletions** → include repair; expose `efRepair` to tune; consider periodic rebuild for heavy‑churn workloads.
* **GC pressure** → avoid boxing; reuse arrays; thread‑local scratch.
* **Pathological distance functions** → works but navigability may degrade; document guidance (metric‑like functions perform best).
* **Concurrency bugs** → start single‑threaded core; add coarse RW lock later.

---

This plan is designed so you can implement **milestone by milestone**, with measurable correctness and performance at every step. If you want, I can provide **reference pseudocode** for the neighbor selection heuristic and the EF‑search loops in Java‑style next.
