package com.wjc.distributedlock.projo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("db_lock")
public class Lock {

//    @TableId(type = IdType.AUTO)
    private Long id;
    private String lockName;
}
