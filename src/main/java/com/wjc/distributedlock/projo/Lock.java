package com.wjc.distributedlock.projo;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("db_lock")
public class Lock {

    private Long id;
    private String lockName;
}
