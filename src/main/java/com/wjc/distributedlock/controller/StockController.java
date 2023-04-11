package com.wjc.distributedlock.controller;

import com.wjc.distributedlock.service.StockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StockController {

    @Autowired
    private StockService stockService;

    @GetMapping("stock/deduct")
    public String deduct() {
        this.stockService.deduct();
        return "hello stock deduct!!";
    }

    @GetMapping("test/latch")
    public String testLatch() {
        this.stockService.testLatch();
        return "班长锁门了。。。。";
    }

    @GetMapping("test/countdown")
    public String testCountDown() {
        this.stockService.testCountDown();
        return "出来了一位同学。。。。";
    }

    @GetMapping("test/zk/read/lock")
    public String testZkReadLock() {
        this.stockService.testZkReadLock();
        return "测试读锁";
    }

    @GetMapping("test/zk/write/lock")
    public String testZkWriteLock() {
        this.stockService.testZkWriteLock();
        return "测试写锁";
    }

    @GetMapping("test/share/count")
    public String testShareCount() {
        this.stockService.testShareCount();
        return "测试共享计数器";
    }

}
