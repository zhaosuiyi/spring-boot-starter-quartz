package com.yfs.springboot.quartz.service;

import com.baomidou.mybatisplus.plugins.Page;
import com.mybaitsplus.devtools.core.base.BaseService;
import com.mybaitsplus.devtools.core.util.InstanceUtil;
import com.yfs.springboot.quartz.mapper.TaskFireLogMapper;
import com.yfs.springboot.quartz.model.TaskFireLog;
import com.yfs.springboot.quartz.model.TaskScheduled;
import com.yfs.springboot.quartz.manager.SchedulerManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 */
@Service
public class SchedulerService implements ApplicationContextAware {
	@Autowired
	private TaskFireLogMapper taskFireLogMapper;
	@Autowired
	private SchedulerManager schedulerManager;
	protected ApplicationContext applicationContext;

	public void setApplicationContext(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}
	
	// 获取所有作业
	public List<TaskScheduled> getAllTaskDetail() {
		return schedulerManager.getAllJobDetail();
	}

	// 执行作业
	public void execTask(TaskScheduled taskScheduler) {
		schedulerManager.runJob(taskScheduler);
	}

	// 恢复作业
	public void openTask(TaskScheduled taskScheduled) {
		schedulerManager.resumeJob(taskScheduled);
	}

	// 暂停作业
	public void closeTask(TaskScheduled taskScheduled) {
		schedulerManager.stopJob(taskScheduled);
	}

	// 删除作业
	public void delTask(TaskScheduled taskScheduled) {
		schedulerManager.delJob(taskScheduled);
	}

	// 修改任务
	public void updateTask(TaskScheduled taskScheduled) {
		schedulerManager.updateTask(taskScheduled);
	}

	@Cacheable("taskFireLog")
	public TaskFireLog getFireLogById(Long id) {
		return taskFireLogMapper.selectById(id);
	}

	@Transactional
	@CachePut("taskFireLog")
	public TaskFireLog updateLog(TaskFireLog record) {
		if (record.getId() == null) {
			taskFireLogMapper.insert(record);
		} else {
			taskFireLogMapper.updateById(record);
		}
		return record;
	}

	public Page<TaskFireLog> queryLog(Map<String, Object> params) {
		Page<Long> ids = BaseService.getPage(params);
		ids.setRecords(taskFireLogMapper.selectIdByMap(ids, params));
		Page<TaskFireLog> page = new Page<TaskFireLog>(ids.getCurrent(), ids.getSize());
		page.setTotal(ids.getTotal());
		if (ids != null) {
			List<TaskFireLog> records = InstanceUtil.newArrayList();
			for (Long id : ids.getRecords()) {
				records.add(applicationContext.getBean(getClass()).getFireLogById(id));
			}
			page.setRecords(records);
		}
		return page;
	}
}
