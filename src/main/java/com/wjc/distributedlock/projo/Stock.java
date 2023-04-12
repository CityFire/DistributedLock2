package com.wjc.distributedlock.projo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wjc.distributedlock.mapper.StockMapper;
import com.wjc.distributedlock.projo.query.StockQuery;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@TableName("db_stock")  // 表名与编码开发设计不同步
@Data
public class Stock {

    private Long id;

    @TableField(value = "product_code") // 字段映射与表名映射不匹配
    private String productCode;

    private String warehouse;

    private Integer count;

//    @Version
    private Integer version;

    @TableField(exist = false)
    private Integer stock = 5000;

    // 编码中添加了数据库中未定义的属性
    @TableField(exist = false)
    private Integer online;
    // 采用默认查询开放了更多的字段查看权限 select=false
    // value:设置数据库表字段名称
    // exist:设置属性在数据库表字段中是否存在，默认为true。此属性无法与value合并使用
    // select:设置属性是否参与查询，此属性与select()映射配置不冲突
    // 表名与编码开发设计不同步  `tbl_user` @TableName("tbl_user")
    @TableField(exist = false, select = false)
    private String password;

    // 逻辑删除字段，标记当前记录是否被删除
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
}

class StockTest {

    @Autowired
    private static StockMapper stockMapper;

    public static void main(String[] args) {

//        testGetAll();
        testGetById();
//        testGetByPage();
//        testDelete();

    }

    static private void testGetById() {
        Stock stock = stockMapper.selectById(2);
        System.out.println(stock);
    }

    static private void testGetAll() {
        List<Stock> stockList = stockMapper.selectList(null);
        System.out.println(stockList);
    }

    static private void testGetByPage() { // 分页
        IPage page = new Page(1, 2);
        stockMapper.selectPage(page, null);
        System.out.println("当前页码值：" + page.getCurrent());
        System.out.println("每页显示数：" + page.getSize());
        System.out.println("一共多少页：" + page.getPages());
        System.out.println("一共多少条数据：" + page.getTotal());
        System.out.println("数据：" + page.getRecords());
    }

    static private void testGetAllByWrapper() {
        // 方式一：按条件查询
        /*QueryWrapper<Stock> qw = new QueryWrapper<Stock>();
        qw.lt("stock", 2000);
        List<Stock> selectList = stockMapper.selectList(qw);
        System.out.println(selectList);*/

//        // 方式二：lambda格式按条件查询
//        QueryWrapper<Stock> qw = new QueryWrapper<Stock>();
//        qw.lambda().lt(Stock::getStock, 2000);
//        List<Stock> selectList = stockMapper.selectList(qw);
//        System.out.println(selectList);

        // 方式三：lambda格式按条件查询
//        LambdaQueryWrapper<Stock> lqw = new LambdaQueryWrapper<Stock>();
//        lqw.lt(Stock::getStock, 2000);
//        List<Stock> selectList = stockMapper.selectList(lqw);
//        System.out.println(selectList);

        // 链式编程
//        LambdaQueryWrapper<Stock> lqw = new LambdaQueryWrapper<Stock>();
//        // 2000到3500之间
//        lqw.lt(Stock::getStock, 2000).gt(Stock::getStock, 3500);
//        // 小于2000或者大于3500
//        lqw.lt(Stock::getStock, 2000).or().gt(Stock::getStock, 3500);
//        List<Stock> selectList = stockMapper.selectList(lqw);
//        System.out.println(selectList);

        // 模拟页面传递过来的查询数据
        StockQuery sq = new StockQuery();
//        sq.setStock(1000);
        sq.setStock2(4000);

        // null判定
//        LambdaQueryWrapper<Stock> lqw = new LambdaQueryWrapper<Stock>();
//        lqw.lt(Stock::getStock, sq.getStock2());
//        if (null != sq.getStock()) {
//            lqw.gt(Stock::getStock, sq.getStock());
//        }
//        List<Stock> stockList = stockMapper.selectList(lqw);
//        System.out.println(stockList);

        LambdaQueryWrapper<Stock> lqw = new LambdaQueryWrapper<Stock>();
        // 先判定第一个参数是否为true，如果为true连接当前条件
//        lqw.lt(null != sq.getStock2(), Stock::getStock, sq.getStock2());
//        lqw.gt(null != sq.getStock(), Stock::getStock, sq.getStock());
        lqw.lt(null != sq.getStock2(), Stock::getStock, sq.getStock2())
           .gt(null != sq.getStock(), Stock::getStock, sq.getStock());
        List<Stock> stockList = stockMapper.selectList(lqw);
        System.out.println(stockList);

    }

    static private void select() {
        // 查询投影
//        LambdaQueryWrapper<Stock> lqw = new LambdaQueryWrapper<Stock>();
//        lqw.select(Stock::getId, Stock::getProductCode, Stock::getWarehouse);
//        List<Stock> stockList = stockMapper.selectList(lqw);
//        System.out.println(stockList);

//        QueryWrapper<Stock> lqw = new QueryWrapper<Stock>();
//        lqw.select("id", "productCode", "warehouse");
//        List<Stock> stockList = stockMapper.selectList(lqw);
//        System.out.println(stockList);

        // 分组查询聚合函数
        QueryWrapper<Stock> lqw = new QueryWrapper<Stock>();
        lqw.select("count(*) as count, warehouse");
        lqw.groupBy("warehouse");
        List<Map<String, Object>> stockList = stockMapper.selectMaps(lqw);
        System.out.println(stockList);
    }

    static private void testSelect() {
        // 条件查询
        LambdaQueryWrapper<Stock> lqw = new LambdaQueryWrapper<Stock>();
        // 等同于=
        lqw.eq(Stock::getProductCode, "10010").eq(Stock::getWarehouse, "上海仓库");
        Stock stock = stockMapper.selectOne(lqw);
        System.out.println(stock);
    }

    static private void  testSelectScope() {
        // 条件查询
//        LambdaQueryWrapper<Stock> lqw = new LambdaQueryWrapper<Stock>();
//        // 查询范围 lt le gt ge eq between
//        lqw.between(Stock::getProductCode, "10010", "10100");
//        List<Stock> stockList = stockMapper.selectList(lqw);
//        System.out.println(stockList);

        LambdaQueryWrapper<Stock> lqw = new LambdaQueryWrapper<Stock>();
        // 模糊匹配 like
        lqw.like(Stock::getWarehouse, "上海");
//        lqw.likeRight(Stock::getWarehouse, "深圳");
        List<Stock> stockList = stockMapper.selectList(lqw);
        System.out.println(stockList);
    }

    static private void testInsert() {
        Stock stock = new Stock();
        stock.setId(1433333333L);
        stock.setStock(3500);
        stock.setWarehouse("湛江仓库");
        stock.setProductCode("10759");
        stock.setCount(30);
        stockMapper.insert(stock);
    }

    static private void testUpdate() {
        Stock stock = new Stock();
        stock.setId(1368888888L);
        stock.setStock(5000);
        stock.setWarehouse("湛江仓库");
//        stock.setVersion(1);
        stockMapper.updateById(stock);
    }

    static private void testDelete() {
        // 删除多条记录
        ArrayList<Long> list = new ArrayList<>();
        list.add(6L);
        list.add(6L);
        stockMapper.deleteBatchIds(list);

        stockMapper.deleteById(6L);
        System.out.println(stockMapper.selectList(null));

        // 查询多条记录
//        ArrayList<Long> list = new ArrayList<>();
//        list.add(1L);
//        list.add(2L);
//        stockMapper.selectBatchIds(list);
    }

}
