import org.apache.curator.test.TestingServer;

public class ZkStart {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 2181;
        TestingServer zk = new TestingServer(port, true);
        System.out.println("ZK started on port " + zk.getConnectString());
        System.out.println("Press Ctrl+C to stop");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { zk.close(); } catch (Exception e) { e.printStackTrace(); }
        }));
        Thread.currentThread().join();
    }
}
