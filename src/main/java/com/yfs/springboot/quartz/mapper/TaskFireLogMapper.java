package com.yfs.springboot.quartz.mapper;

import com.mybaitsplus.devtools.core.base.BaseMapper;
import com.yfs.springboot.quartz.model.TaskFireLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.RowBounds;

import java.util.List;
import java.util.Map;

public interface TaskFireLogMapper extends BaseMapper<TaskFireLog> {
    List<Long> selectIdByMap(RowBounds rowBounds, @Param("cm") Map<String, Object> params);
}
