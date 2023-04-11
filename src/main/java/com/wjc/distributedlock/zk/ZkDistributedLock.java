package com.wjc.distributedlock.zk;

import jodd.util.CollectionUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.zookeeper.*;
import org.springframework.util.CollectionUtils;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

public class ZkDistributedLock implements Lock {

    private ZooKeeper zooKeeper;

    private String lockName;

    private String currentNodePath;

    private static final String ROOT_PATH = "/locks";

    private static final ThreadLocal<Integer> THREAD_LOCAL = new ThreadLocal<>();

    public ZkDistributedLock(ZooKeeper zooKeeper, String lockName) {
        this.zooKeeper = zooKeeper;
        this.lockName = lockName;
        try {
            if (zooKeeper.exists(ROOT_PATH, false) == null) {
                zooKeeper.create(ROOT_PATH, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            }
        } catch (KeeperException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void lock() {
        // 创建znode节点过程
        this.tryLock();
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {

    }

    @Override
    public boolean tryLock() {
        try {
            // 判断threadLocal中是否已经有锁，有锁直接重入（+1）
            Integer flag = THREAD_LOCAL.get();
            if (flag != null && flag > 0) {
                THREAD_LOCAL.set(flag + 1);
                return true;
            }
            // 创建znode节点过程:为了防止zk客户端程序获取到锁之后，服务器宕机带来的死锁问题，这里创建的是临时节点
            // 所有请求要求获取锁时，给每一个请求创建临时序列化节点
            currentNodePath = this.zooKeeper.create(ROOT_PATH + "/" + lockName + "-", null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
            // 获取前置节点，如果前置节点为空，则获取锁成功，否则监听前置节点
            String preNode = this.getPreNode();
            if (preNode != null) {
                // 利用闭锁思想，实现阻塞功能
                CountDownLatch countDownlatch = new CountDownLatch(1);
                // 因为获取前置节点这个操作，不具备原子性。再次判断zk中的前置节点是否存在
                if (this.zooKeeper.exists(ROOT_PATH + "/" + preNode, new Watcher() {
                    @Override
                    public void process(WatchedEvent watchedEvent) {
                        countDownlatch.countDown();
                    }
                }) == null) {
                    THREAD_LOCAL.set(1);
                    return true;
                }
                countDownlatch.await();
            }

            THREAD_LOCAL.set(1);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
//            try {
//                Thread.sleep(80);
//                this.tryLock();
//            } catch (InterruptedException ex) {
//                ex.printStackTrace();
//            }
        }
        return false;
    }

    private String getPreNode() {
        try {
            // 获取根节点下的所有节点
            List<String> children = this.zooKeeper.getChildren(ROOT_PATH, false);
            if (CollectionUtils.isEmpty(children)) {
                throw new IllegalMonitorStateException("非法操作！");
            }

            // 获取和当前节点同一资源的锁
            List<String> nodes = children.stream().filter(node -> StringUtils.startsWith(node, lockName + "-")).collect(Collectors.toList());
            if (CollectionUtils.isEmpty(nodes)) {
                throw new IllegalMonitorStateException("非法操作！");
            }

            // 排好队
            Collections.sort(nodes);

            // 获取当前节点的下标
            String currentNode = StringUtils.substringAfterLast(currentNodePath, "/"); // 获取当前节点
            int index = Collections.binarySearch(nodes, currentNode);
            if (index < 0) {
                throw new IllegalMonitorStateException("非法操作！");
            } else if (index > 0) {
                return nodes.get(index - 1); // 返回前置节点
            }
            // 如果当前节点就是第一个节点，则返回null
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalMonitorStateException("非法操作！");
        }
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        return false;
    }

    @Override
    public void unlock() {
        try {
            THREAD_LOCAL.set(THREAD_LOCAL.get() - 1);
            if (THREAD_LOCAL.get() == 0) {
                // 删除znode节点的过程
                this.zooKeeper.delete(currentNodePath, -1);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (KeeperException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Condition newCondition() {
        return null;
    }

}
