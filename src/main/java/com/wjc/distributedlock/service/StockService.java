package com.wjc.distributedlock.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.wjc.distributedlock.mapper.StockMapper;
import com.wjc.distributedlock.projo.Stock;
import jodd.util.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

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
 3.原子性：
 获取锁和过期时间之间：set key value ex 3 nx
 4.防误删：解铃还须系铃人
 先判断再删除
 5.自动续期
 操作：
 1.加锁 setnx
 2.解锁 del
 3.重试：递归 循环
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

    public void deduct() {
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
            if (StringUtil.equals(this.redisTemplate.opsForValue().get("lock"), uuid)) {
                this.redisTemplate.delete("lock");
            }
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
