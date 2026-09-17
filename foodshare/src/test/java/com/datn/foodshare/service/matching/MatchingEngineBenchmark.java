package com.datn.foodshare.service.matching;

import com.datn.foodshare.domain.entity.*;
import com.datn.foodshare.util.constant.*;
import com.datn.foodshare.service.matching.MinimumCostMaximumFlowService.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

/** Reproducible in-process microbenchmark. Excludes HTTP, MySQL and SMTP; not a production SLA. */
public final class MatchingEngineBenchmark {
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    private static final int WARMUP = 3, SAMPLES = 15;
    private static final List<String> CSV = new ArrayList<>();
    private static volatile Object sink;

    public static void main(String[] args) throws Exception {
        CSV.add("dataset,posts,receivers,edges,operation,samples,p50_ms,p95_ms,heap_used_mib");
        StringBuilder report = new StringBuilder("# Matching benchmark\n\n")
                .append("Java: ").append(System.getProperty("java.version")).append("\n\n")
                .append("CPU cores visible: ").append(Runtime.getRuntime().availableProcessors()).append("\n\n")
                .append("JVM max heap MiB: ").append(Runtime.getRuntime().maxMemory() / 1048576).append("\n\n")
                .append("Seed=7331; warmup=3; samples=15; allocation selects up to 50 posts; initial K=3.\n\n")
                .append("Excluded: DB/HTTP/auth/UI. Heap is used JVM heap at sampling, not exact retained graph memory. ")
                .append("Percentiles from 15 samples are descriptive; this is not JMH or a production load test.\n\n")
                .append("| Dataset | Nodes P/C | Edges | Operation | p50 ms | p95 ms |\n|---|---|---|---|---|---|\n");
        run("small", 100, 250, 5, report);
        run("medium", 300, 750, 10, report);
        run("large", 600, 1500, 10, report);
        run("dense", 150, 400, 1, report);
        Path output = Path.of("build", "reports", "matching-benchmark");
        Files.createDirectories(output);
        Files.write(output.resolve("results.csv"), CSV);
        Files.writeString(output.resolve("RESULTS.md"), report.toString());
        System.out.println(report);
        System.out.println("Reports: " + output.toAbsolutePath());
    }

    private static void run(String name, int postCount, int receiverCount, int clusters, StringBuilder report) {
        Random random = new Random(7331);
        List<FoodPost> posts = new ArrayList<>();
        List<User> receivers = new ArrayList<>();
        Map<Long, Long> counts = new HashMap<>();
        for (int i = 0; i < receiverCount; i++) {
            long id = 100000L + i;
            receivers.add(User.builder().id(id).role(i % 4 == 0 ? Role.RECIPIENT : Role.ORGANIZATION)
                    .active(true).profileCompleted(true).fullName("Receiver " + id)
                    .latitude(BigDecimal.valueOf(10.77 + (i % clusters) * .3 + random.nextDouble() * .025))
                    .longitude(BigDecimal.valueOf(106.7 + random.nextDouble() * .025)).build());
            counts.put(id, (long) (i % 3));
        }
        for (int i = 0; i < postCount; i++) {
            User supplier = User.builder().id((long) i + 1).role(Role.SUPPLIER)
                    .latitude(BigDecimal.valueOf(10.77 + (i % clusters) * .3))
                    .longitude(BigDecimal.valueOf(106.7)).build();
            posts.add(FoodPost.builder().id((long) i + 1)
                    .businessProfile(BusinessProfile.builder().id((long) i + 1).user(supplier).build())
                    .postStatus(PostStatus.AVAILABLE).availableQuantity(3)
                    .expiresAt(NOW.plusSeconds(7200 + i * 3L)).pickupEndAt(NOW.plusSeconds(7200))
                    .createdAt(NOW.minusSeconds(i)).build());
        }
        DynamicMatchingGraph graph = new DynamicMatchingGraph();
        measure(name, postCount, receiverCount, postCount * receiverCount / clusters, "rebuild",
                () -> { graph.rebuild(posts, receivers, counts); return graph.foodPostCount(); }, report);
        int edges = graph.edgeCount();
        measure(name, postCount, receiverCount, edges, "recommend_best_ram",
                () -> MatchingPipelineService.rankForCurrentUser(graph.snapshotForCandidate(100000, NOW).posts(),
                        MatchingPipelineService.RecommendationMode.BEST_MATCH).stream().limit(6).toList(), report);
        User moved = receivers.get(0);
        measure(name, postCount, receiverCount, edges, "candidate_update",
                () -> { moved.setLatitude(moved.getLatitude().add(new BigDecimal("0.000001"))); graph.synchronizeCandidate(moved, 0); return graph.getCandidateIds(1); }, report);
        measure(name, postCount, receiverCount, edges, "post_update",
                () -> { posts.get(0).setAvailableQuantity(posts.get(0).getAvailableQuantity() == 3 ? 2 : 3); graph.synchronizeFoodPost(posts.get(0)); return graph.getCandidateIds(1); }, report);
        var sets = graph.snapshot(NOW).posts().stream().limit(50)
                .map(p -> new TopKCandidateSet(p.post().toFoodPost(), p.scores())).toList();
        Map<Long, Integer> capacities = new HashMap<>();
        receivers.forEach(u -> capacities.put(u.getId(), 10));
        measure(name, postCount, receiverCount, edges, "mcmf_full_cold",
                () -> new MinimumCostMaximumFlowService().allocate(sets, capacities, NOW), report);
        measure(name, postCount, receiverCount, edges, "adaptive_cold_certified",
                () -> new AdaptiveTopKAllocator(new IncrementalAllocationService(new MinimumCostMaximumFlowService()))
                        .allocate(sets, capacities, 3, NOW), report);
        var incremental = new IncrementalAllocationService(new MinimumCostMaximumFlowService());
        var adaptive = new AdaptiveTopKAllocator(incremental);
        measure(name, postCount, receiverCount, edges, "adaptive_same_input_cached",
                () -> adaptive.allocate(sets, capacities, 3, NOW), report);
        final int[] tick = {0};
        measure(name, postCount, receiverCount, edges, "adaptive_time_changed",
                () -> {
                    Instant time = NOW.plusSeconds(++tick[0]);
                    var changed = graph.snapshot(time).posts().stream().limit(50)
                            .map(p -> new TopKCandidateSet(p.post().toFoodPost(), p.scores())).toList();
                    return adaptive.allocate(changed, capacities, 3, time);
                }, report);
        System.out.println(name + " cache stats: " + incremental.statistics());
    }

    private static void measure(String name, int posts, int receivers, int edges, String operation,
                                Supplier<Object> action, StringBuilder report) {
        for (int i = 0; i < WARMUP; i++) sink = action.get();
        double[] times = new double[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) {
            long start = System.nanoTime();
            sink = action.get();
            times[i] = (System.nanoTime() - start) / 1000000.0;
        }
        Arrays.sort(times);
        double p50 = times[SAMPLES / 2], p95 = times[(int) Math.ceil(SAMPLES * .95) - 1];
        double heap = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576.0;
        CSV.add(String.format(Locale.ROOT, "%s,%d,%d,%d,%s,%d,%.3f,%.3f,%.2f",
                name, posts, receivers, edges, operation, SAMPLES, p50, p95, heap));
        report.append(String.format(Locale.ROOT, "| %s | %d/%d | %d | %s | %.3f | %.3f |\n",
                name, posts, receivers, edges, operation, p50, p95));
        System.out.printf(Locale.ROOT, "%s %s p50=%.3fms p95=%.3fms%n", name, operation, p50, p95);
    }
}
