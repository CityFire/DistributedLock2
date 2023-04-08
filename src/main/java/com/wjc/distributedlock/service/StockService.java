package com.wjc.distributedlock.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.wjc.distributedlock.mapper.StockMapper;
import com.wjc.distributedlock.projo.Stock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

 redis
 一、JVM本地锁机制
 二、redis乐观锁： watch multi exec
 三、分布式锁
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

//    @Transactional  // MDL 更新 新增 删除 事务注解导致加锁 阻塞
    public void deduct() {
        // 1.查询库存信息
        String stock = this.redisTemplate.opsForValue().get("stock");

        // 2.判断库存是否充足
        if (stock != null && stock.length() != 0) {
            Integer st = Integer.valueOf(stock);
            if (st > 0) {
                // 3.扣减库存
                this.redisTemplate.opsForValue().set("stock", String.valueOf(--st));
            }
        }


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
