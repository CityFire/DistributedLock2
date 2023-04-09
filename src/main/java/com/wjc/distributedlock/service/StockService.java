package com.wjc.distributedlock.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.wjc.distributedlock.mapper.StockMapper;
import com.wjc.distributedlock.projo.Stock;
import com.wjc.distributedlock.zk.ZkClient;
import com.wjc.distributedlock.zk.ZkDistributedLock;
import jodd.util.StringUtil;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/*
mysql
 一、JVM本地锁：三种情况导致锁失效：1.多例模式 2.事务:（例外：Read Uncommitted可以解决，但业务不允许）3.集群部署
 二、一个sql语句：更新数量时判断 解决：三个锁失效
 问题：1.锁范围问题 表级锁 行级锁 2.同一个商品有多条库存记录 3.无法记录库存变化前后的状态
 三、悲观锁：select... for update
 问题：1、性能问题 2.死锁问题：对多条数据加锁时，加锁顺序要一致  3.库存操作要统一：select... for update 普通select
mysql悲观锁中使用行级锁：1.锁的查询或者更新条件必须是索引字段 2.查询或者更新条件必须是具体值
 四、乐观锁：时间戳 version版本号 CAS机制(Compare And Swap(Set),比较并交换变量x 旧值A 新值B)
 问题：1.高并发情况下，性能极低 2.ABA问题 3.读写分离情况下导致乐观锁不可靠

 性能：二>三>一>四  2>3>1>4
 场景：三>四>二>一 3>4>2>1

 redis
 一、JVM本地锁机制
 二、redis乐观锁：400 不建议使用
 watch：可以监控一个或者多个key的值，如果在事务（exec）执行之前，key的值发生变化则取消事务执行
 multi：开启事务
 exec：执行事务
 三、分布式锁：跨进程 跨服务 跨服务器 场景：超卖线程（NoSQL）缓存击穿 一个热点key过期
 分布式锁的实现方式：
 1.基于redis实现
 2.基于zookeeper/etcd实现
 3.基于mysql实现
 特征：
 1.独占排他使用  setnx
 2.防死锁发生
 如果redis客户端程序从redis服务中获取到锁之后立马宕机。
 解决：给锁添加过期时间。expire
 不可重入：可重入性
 3.原子性：
 获取锁和过期时间之间：set key value ex 3 nx
 判断和释放锁之间：lua脚本
 4.防误删：解铃还须系铃人
 先判断再删除
 5.可重入性:hash+lua脚本
 6.自动续期：Timer定时器+lua脚本
 7.在集群情况下，导致锁机制失效：
 1.客户端程序10010，从主中获取锁
 2.从还没来得及同步数据，主挂了
 3.于是从升级为主
 4.客户端程序10086就从新主中获取到锁，导致锁机制失效
 操作：
 1.加锁 setnx
 2.解锁 del
 3.重试：递归 循环

 lua脚本
 一次性发送多个指令给redis，redis单线程 执行指令遵守one-by-one规则
 EVAL script numkeys key [key...] arg [arg...] 输出的不是print，而是return
   script:lua脚本字符串
   numkeys:key列表的元素数量
   key列表:以空格分割。KEYS[index从1开始]
   arg列表:以空格分割。ARGV[index从1开始]

 变量：
 全局变量：a=5
 局部变量：local a=5

 分支控制：
 if 条件
 then
    代码块
 elseif 条件
 then
   代码块
 else
   代码块
 end

 eval "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end" 1 lock 123213-12-233-443333

 if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end

 if redis.call('get', KEYS[1]) == ARGV[1]
 then
    return redis.call('del', KEYS[1])
 else
    return 0
 end

 key: lock
 arg:uuid


红锁算法Redlock
redis集群状态下的问题:
1.客户端A从master获取到锁
2.在master将锁同步到slave之前，master宕掉了。
3.slave节点被晋级为master节点
4.客户端B取得了同一个资源被客户端A已经获取到的另外一个锁。
安全失效!
解决集群下锁失效，参照redis官方网站针对redlock文档: https://redis.io/topics/distlock
在算法的分布式版本中，我们假设有N个Redis服务器。这些节点是完全独立的，因此我们不使用复制或任何其他隐式协调系统。
前几节已经描述了如何在单个实例中安全地获取和释放锁，在分布式锁算法中，将使用相同的方法在单个实例中获取和释放锁。
将N设置为5是一个合理的值，因此需要在不同的计算机或虚拟机上运行5个Redis主服务器，确保它们以独立的方式发生故障。
为了获取锁，客户端执行以下操作:
1.客户端以毫秒为单位获取当前时间的时间戳，作为起始时间。
2.客户端尝试在所有N个实例中顺序使用相同的键名、相同的随机值来获取锁定。每个实例尝试获取锁都需要时间，客户端应该
设置一个远小于总锁定时间的超时时间。例如，如果自动释放时间为10秒，则尝试获取锁的超时时间可能在5到50毫秒之间。这
样可以防止客户端长时间与处于故障状态的Redis节点进行通信: 如果某个实例不可用，尽快尝试与下一个实例进行通信。
3.客户端获取当前时间 减去在步骤1中获得的起始时间，来计算获取锁所花费的时间。当目仅当客户端能够多数实例(至少3个)
中获取锁时，并且获取锁所花费的总时间小于锁有效时间，则认为已获取锁。
4.如果获取了锁，则将锁有效时间减去获取锁所花费的时间，如步骤3中所计算。
5.如果客户端由于某种原因(无法锁定N /2 +1个实例或有效时间为负)而未能获得该锁，它将尝试解锁所有实例(即使没有锁定成功的实例) 。
每台计算机都有一个本地时钟，我们通常可以依靠不同的计算机来产生很小的时钟漂移。只有在拥有锁的客户端将在锁有效时间
内(如步骤3中获得的) 减去一段时间(仅几毫秒)的情况下终止工作，才能保证这一点。以补偿进程之间的时钟漂移

RedLock算法:
1.应用程序获取系统当前时间
2.应用程序使用相同的kv值依次从多个redis实例中获取锁。如果某一个节点超过定时间依然没有获取到锁则直接放弃，
尽快尝试从下一个健康的redis节点获取锁，以避免被一个宕机了的节点阻塞
3.计算获取锁的消耗时间 = 客户端程序的系统当前时间 - step1中的时间。获取锁的消耗时间小于总的锁定时间 (30s)
并且半数以上节点获取锁成功，认为获取锁成功
4.计算剩余锁定时间 = 总的锁定时间 -step3中的消耗时间
5.如果获取锁失败了，对所有的redis节点释放锁。


redisson:redis的java客户端，分布式锁
玩法:
   1.引入依赖
   2.java配置类: RedissonConfig
     @Bean
     public RedissonClient redissonClient() {
         Config config = new Config() ;
         config.usesingleServer () .setAddress("redis://ip:port") ;
         return Redisson.create(config);
    }
3.代码使用:
    可重入锁RLock对象
        RLock lock = this.redissonClient.getLock("xxx");
        lock.lock() /unlock()
公平锁:
   RLock lock = this.redissonClient.getFairLock("xxx");
   lock.lock() /unlock()
联锁 和 红锁:
读写锁:
   RReadWriteLock rwlock = this,redissonclient.getReadWritelock("xxx");
   rwLock.readLock() .lock() /unlock();
   rwlock.writelock() .lock() /unlock();
信号量:
   RSemaphore semaphore = this.redissonClient.getSemaphore("xxx");
   semaphore.trysetPermits(3);
   semaphore.acquire() /release();
闭锁:
   RCountDownlatch cdl = this.redissonclient.getCountDownlatch("xxx");
   cdl.trysetCount(6);
   cdl.await() /countDowntch();



zookeeper分布式锁:
   1.介绍了zk
   2.zk下载及安装
   3.指令:
       ls
       get /zookeeper
       create /aa "test"
       delete /aa
       set /aa "test1"
   4.znode节点类型:
       永久节点: create /path content
       临时节点: create -e /path content 只有客户端程序断开链接自动删除
       永久序列化节点: create -s /path content
       临时序列化节点: create -s -e /path content
   5.节点的事件监听:
       1.节点创建 NodeCreated
           stat -w /xx
       2.节点删除 NodeDeleted
           stat -w /xx
       3.节点数据变化 NodeDataChanged
           get -w /xx
       4.子节点变化 NodeChildrenChanged
           ls -w /xx
    6.java客户端：官方提供 ZkClient Curator
    7.分布式锁：
      1.独占排他：znode节点不可重复 自旋锁
      2.阻塞锁：临时序列化节点
         1.所有请求要求获取锁时，给每一个请求创建临时序列化节点
         2.获取当前节点的前置节点，如果前置节点为空，则获取锁成功，否则监听前置节点
         3.获取锁成功之后执行业务操作，然后释放当前节点的锁
      3.可重入: 同一线程已经获取过该锁的情况下，可重入
         1.在节点的内容中记录服务器、线程以及重入信息
         2.ThreadLocal:线程的局部变量，线程私有

 */
@Service
//@Scope(value = "prototype", proxyMode = ScopedProxyMode.TARGET_CLASS) // 多例模式
public class StockService {

    //    private Stock stock = new Stock();
    @Autowired
    private StockMapper stockMapper;

    private ReentrantLock lock = new ReentrantLock();

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private ZkClient zkClient;

    public void deduct() {
        ZkDistributedLock lock = this.zkClient.getLock("lock");
        lock.lock();
        try {
            // 1.查询库存信息
            String stock = redisTemplate.opsForValue().get("stock").toString();

            // 2.判断库存是否充足
            if (stock != null && stock.length() != 0) {
                Integer st = Integer.valueOf(stock);
                if (st > 0) {
                    // 3.扣减库存
                    redisTemplate.opsForValue().set("stock", String.valueOf(--st));
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public void deduct7() {
        RLock lock = this.redissonClient.getLock("lock");
//        lock.lock(10, TimeUnit.SECONDS);
        lock.lock();

        try {
            // 1.查询库存信息
            String stock = redisTemplate.opsForValue().get("stock").toString();

            // 2.判断库存是否充足
            if (stock != null && stock.length() != 0) {
                Integer st = Integer.valueOf(stock);
                if (st > 0) {
                    // 3.扣减库存
                    redisTemplate.opsForValue().set("stock", String.valueOf(--st));
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public void deduct6() {
        // 加锁setnx
        String uuid = UUID.randomUUID().toString();
        while (!this.redisTemplate.opsForValue().setIfAbsent("lock", uuid, 3, TimeUnit.SECONDS)) {
            // 重试，循环
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        try {
//            this.redisTemplate.expire("lock", 3, TimeUnit.SECONDS);
            // 1.查询库存信息
            String stock = redisTemplate.opsForValue().get("stock").toString();

            // 2.判断库存是否充足
            if (stock != null && stock.length() != 0) {
                Integer st = Integer.valueOf(stock);
                if (st > 0) {
                    // 3.扣减库存
                    redisTemplate.opsForValue().set("stock", String.valueOf(--st));
                }
            }
        } finally {
            // 先判断是否自己的锁，再解锁
            String script = "if redis.call('get', KEYS[1]) == ARGV[1]" +
                    "then " +
                    " return redis.call('del', KEYS[1])" +
                    "else " +
                    " return 0 " +
                    "end";
            this.redisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), Arrays.asList("lock"), uuid);
//            if (StringUtil.equals(this.redisTemplate.opsForValue().get("lock"), uuid)) {
//                this.redisTemplate.delete("lock");
//            }
        }
    }

    public void deduct5() {
        this.redisTemplate.execute(new SessionCallback<Object>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                // watch
                operations.watch("stock");
                // 1.查询库存信息
                String stock = operations.opsForValue().get("stock").toString();

                // 2.判断库存是否充足
                if (stock != null && stock.length() != 0) {
                    Integer st = Integer.valueOf(stock);
                    if (st > 0) {
                        // multi
                        operations.multi();
                        // 3.扣减库存
                        operations.opsForValue().set("stock", String.valueOf(--st));
                        // exec 执行事务
                        List exec = operations.exec();
                        // 如果执行事务的返回结果集为空，则代表减库存是吧，重试
                        if (exec == null || exec.size() == 0) {
                            try {
                                Thread.sleep(40);
                                deduct();
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        return exec;
                    }
                }
                return null;
            }

            ;
        });
    }

    //    @Transactional  // MDL 更新 新增 删除 事务注解导致加锁 阻塞
    public void deduct4() {
        // 1.查询库存信息
        List<Stock> stocks = this.stockMapper.selectList(new QueryWrapper<Stock>().eq("product_code", "1001"));
        // 这里取第一个库存
        Stock stock = stocks.get(0);

        // 2.判断库存是否充足
        if (stock != null && stock.getCount() > 0) {
            // 3.扣减库存
            stock.setCount(stock.getCount() - 1);
            Integer version = stock.getVersion();
            stock.setVersion(version + 1);
            if (this.stockMapper.update(stock, new UpdateWrapper<Stock>().eq("id", stock.getId()).eq("version", version)) == 0) {
                // 如果更新失败，则进行重试！
                try {
                    Thread.sleep(20); // 避免栈内存溢出
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                this.deduct();
            }
        }
    }

    @Transactional
    public void deduct3() {
        // 1.查询库存信息并锁定库存信息
        List<Stock> stocks = this.stockMapper.queryStock("1001");
        // 这里取第一个库存
        Stock stock = stocks.get(0);

        // 2.判断库存是否充足
        if (stock != null && stock.getCount() > 0) {
            // 3.扣减库存
            stock.setCount(stock.getCount() - 1);
            this.stockMapper.updateById(stock);
        }
    }

    //    @Transactional(isolation = Isolation.READ_UNCOMMITTED) // 事务
    @Transactional
    public void deduct2() { // synchronized
//        lock.lock();
        try {
            // update insert delete 写操作本身就会加锁
            // update db_stock set count = count - 1 where product_code = '1001' and count >= 1

            this.stockMapper.updateStock("1001", 1);

            // 1.查询库存
//            Stock stock = this.stockMapper.selectOne(new QueryWrapper<Stock>().eq("product_code", "1001"));
//            // 2.判断库存是否充足
//            if (stock != null && stock.getCount() > 0) {
//                stock.setCount(stock.getCount() - 1);
////                System.out.println("库存余量：" + stock.getCount());
//                // 3.更新库存到数据库
//                stockMapper.updateById(stock);
////                stock.setStock(stock.getStock() - 1);
////                System.out.println("库存余量：" + stock.getStock());
//            }
        } finally {
//            lock.unlock();
        }
    }
}
