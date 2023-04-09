package com.wjc.distributedlock.zk;

import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.CountDownLatch;

@Component
public class ZkClient {

    private ZooKeeper zooKeeper;

    @PostConstruct
    public void init() {
        // 获取链接，项目启动
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void destroy() {
        // 释放zk的链接
        try {
            if (zooKeeper != null) {
                zooKeeper.close();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public ZkDistributedLock getLock(String lockName) {
        return new ZkDistributedLock(zooKeeper, lockName);
    }

}
