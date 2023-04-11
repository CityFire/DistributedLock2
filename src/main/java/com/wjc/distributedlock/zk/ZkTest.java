package com.wjc.distributedlock.zk;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class ZkTest {

    public static void main(String[] args) throws InterruptedException {
        ZooKeeper zooKeeper = null;
        CountDownLatch countDownLatch = new CountDownLatch(1);
        try {
            zooKeeper = new ZooKeeper("127.0.0.1:2181", 30000, new Watcher() {
                @Override
                public void process(WatchedEvent watchedEvent) {
                    Event.KeeperState state = watchedEvent.getState();
                    if (Event.KeeperState.SyncConnected.equals(state) && Event.EventType.None.equals(watchedEvent.getType())) {
                        System.out.println("获取链接了吗？" + watchedEvent);
                        countDownLatch.countDown();
                    } else if (Event.KeeperState.Closed.equals(state)) {
                        System.out.println("关闭链接。。。");
                    } else {
                        System.out.println("节点事件。。。");
                    }
                }
            });
            countDownLatch.await();

            // 节点新增：永久节点 临时节点 永久序列化节点 临时序列化节点
//            zooKeeper.create("/wjc/test1", "hello zookeeper".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            zooKeeper.create("/wjc/test2", "hello zookeeper".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
//            zooKeeper.create("/wjc/test3", "hello zookeeper".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT_SEQUENTIAL);
//            zooKeeper.create("/wjc/test4", "hello zookeeper".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
            // 查询 判断节点是否存在 stat
            Stat stat = zooKeeper.exists("/wjc", false);
            if (stat != null) {
                System.out.println("当前节点存在");
            } else {
                System.out.println("当前节点不存在");
            }
            // 获取当前节点中的数据内容 get
            byte[] data = zooKeeper.getData("/wjc", false, stat);
            System.out.println("当前节点的内容：" + new String(data));

            // 获取当前节点的子节点 1s
            List<String> children = zooKeeper.getChildren("/wjc", new Watcher() {
                @Override
                public void process(WatchedEvent watchedEvent) {
                    System.out.println("节点的子节点发生变化。。。");
                }
            });
            System.out.println("当前节点的子节点：" + children);

            // 更新:版本号必须和当前节点的版本号一致，否则更新失败。也可以指定为-1，代表不关心版本号
            zooKeeper.setData("/wjc", "wawa...".getBytes(), stat.getVersion());

            // 删除
            zooKeeper.delete("/wjc/test1", -1);

            System.in.read();

            System.out.println("一顿操作");
        } catch (IOException | KeeperException e) {
            e.printStackTrace();
        } finally {
            if (zooKeeper != null) {
                zooKeeper.close();
            }
        }
    }
}
