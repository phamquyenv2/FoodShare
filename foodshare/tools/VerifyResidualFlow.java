import java.util.*;

public class VerifyResidualFlow {
    private record FlowResult(long flow, double cost) {
    }

    private static final class FlowNetwork {
        private final List<List<FlowEdge>> adjacency;

        private FlowNetwork(int nodeCount) {
            adjacency = new ArrayList<>(nodeCount);
            for (int node = 0; node < nodeCount; node++) {
                adjacency.add(new ArrayList<>());
            }
        }

        private FlowEdge addEdge(int from, int to, long capacity, double cost) {
            FlowEdge forward = new FlowEdge(to, adjacency.get(to).size(), capacity, cost);
            FlowEdge reverse = new FlowEdge(from, adjacency.get(from).size(), 0, -cost);
            adjacency.get(from).add(forward);
            adjacency.get(to).add(reverse);
            return forward;
        }

        private FlowResult solve(int source, int sink) {
            long totalFlow = 0;
            double totalCost = 0;
            int nodeCount = adjacency.size();
            double[] potential = new double[nodeCount];
            final double epsilon = 1e-12;
            while (true) {
                double[] distance = new double[nodeCount];
                Arrays.fill(distance, Double.POSITIVE_INFINITY);
                distance[source] = 0;
                int[] previousNode = new int[nodeCount], previousEdge = new int[nodeCount];
                Arrays.fill(previousNode, -1);
                boolean[] settled = new boolean[nodeCount];
                java.util.PriorityQueue<QueueEntry> queue = new java.util.PriorityQueue<>(
                        Comparator.comparingDouble(QueueEntry::distance).thenComparingInt(QueueEntry::node));
                queue.add(new QueueEntry(source, 0));
                while (!queue.isEmpty()) {
                    QueueEntry entry = queue.remove();
                    int from = entry.node();
                    if (settled[from] || entry.distance() > distance[from] + epsilon) continue;
                    settled[from] = true;
                    List<FlowEdge> edges = adjacency.get(from);
                    for (int index = 0; index < edges.size(); index++) {
                        FlowEdge edge = edges.get(index);
                        if (edge.capacity == 0 || settled[edge.to]) continue;
                        double reducedCost = edge.cost + potential[from] - potential[edge.to];
                        if (reducedCost < -epsilon)
                            throw new IllegalStateException("Infeasible residual potential");
                        double next = distance[from] + Math.max(0, reducedCost);
                        if (next + epsilon < distance[edge.to]) {
                            distance[edge.to] = next;
                            previousNode[edge.to] = from; previousEdge[edge.to] = index;
                            queue.add(new QueueEntry(edge.to, next));
                        }
                    }
                }
                if (previousNode[sink] == -1) return new FlowResult(totalFlow, totalCost);
                for (int node = 0; node < nodeCount; node++)
                    if (Double.isFinite(distance[node])) potential[node] += distance[node];
                long augmentation = Long.MAX_VALUE;
                double pathCost = 0;
                for (int node = sink; node != source; node = previousNode[node]) {
                    FlowEdge edge = adjacency.get(previousNode[node]).get(previousEdge[node]);
                    augmentation = Math.min(augmentation, edge.capacity);
                    pathCost += edge.cost;
                }
                for (int node = sink; node != source; node = previousNode[node]) {
                    FlowEdge edge = adjacency.get(previousNode[node]).get(previousEdge[node]);
                    edge.capacity -= augmentation;
                    adjacency.get(edge.to).get(edge.reverseIndex).capacity += augmentation;
                }
                totalFlow += augmentation; totalCost += augmentation * pathCost;
            }
        }
        private record QueueEntry(int node, double distance) {}
    }

    private static final class FlowEdge {
        private final int to;
        private final int reverseIndex;
        private final long originalCapacity;
        private final double cost;
        private long capacity;

        private FlowEdge(int to, int reverseIndex, long capacity, double cost) {
            this.to = to;
            this.reverseIndex = reverseIndex;
            this.capacity = capacity;
            this.originalCapacity = capacity;
            this.cost = cost;
        }
    }

    private static long bestFlow;
    private static double bestCost;
    private static void enumerate(int unit, List<Integer> units, double[][] costs, int[] capacities, long flow, double cost) {
        if (unit == units.size()) {
            if (flow > bestFlow || (flow == bestFlow && cost < bestCost)) { bestFlow = flow; bestCost = cost; }
            return;
        }
        enumerate(unit + 1, units, costs, capacities, flow, cost);
        int post = units.get(unit);
        for (int receiver = 0; receiver < capacities.length; receiver++) {
            if (capacities[receiver] == 0 || !Double.isFinite(costs[post][receiver])) continue;
            capacities[receiver]--;
            enumerate(unit + 1, units, costs, capacities, flow + 1, cost + costs[post][receiver]);
            capacities[receiver]++;
        }
    }
    public static void main(String[] args) {
        Random random = new Random(4719);
        for (int test = 0; test < 1000; test++) {
            FlowNetwork network = new FlowNetwork(8);
            int[] capacities = new int[3];
            List<Integer> units = new ArrayList<>();
            double[][] costs = new double[3][3];
            for (int receiver = 0; receiver < 3; receiver++) {
                capacities[receiver] = random.nextInt(4);
                network.addEdge(4 + receiver, 7, capacities[receiver], 0);
            }
            for (int post = 0; post < 3; post++) {
                int supply = random.nextInt(3);
                for (int i = 0; i < supply; i++) units.add(post);
                network.addEdge(0, 1 + post, supply, 0);
                for (int receiver = 0; receiver < 3; receiver++) {
                    costs[post][receiver] = random.nextBoolean() ? random.nextInt(101) / 100.0 : Double.POSITIVE_INFINITY;
                    if (Double.isFinite(costs[post][receiver]))
                        network.addEdge(1 + post, 4 + receiver, supply, costs[post][receiver]);
                }
            }
            bestFlow = -1; bestCost = Double.POSITIVE_INFINITY;
            enumerate(0, units, costs, capacities.clone(), 0, 0);
            FlowResult result = network.solve(0, 7);
            if (result.flow() != bestFlow || Math.abs(result.cost() - bestCost) > 1e-8)
                throw new AssertionError("Mismatch at dataset " + test + ": " + result + ", oracle " + bestFlow + "/" + bestCost);
        }
        System.out.println("PASS: 1000 random networks agree with exhaustive maximum-flow/minimum-cost oracle.");
    }
}
