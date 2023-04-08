package com.wjc.distributedlock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wjc.distributedlock.projo.Stock;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface StockMapper extends BaseMapper<Stock> {
    @Update("update db_stock set count = count - #{count} where product_code = #{product_code} and count >= #{count}")
    int updateStock(@Param("product_code") String productCode, @Param("count") Integer count);

    @Select("select * from db_stock where product_code=#{productCode} for update")
    List<Stock> queryStock(String productCode);
}
