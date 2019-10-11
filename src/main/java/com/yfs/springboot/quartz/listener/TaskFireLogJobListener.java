package com.yfs.springboot.quartz.listener;

import com.alibaba.fastjson.JSON;
import com.mybaitsplus.devtools.core.Constants;
import com.mybaitsplus.devtools.core.support.email.Email;
import com.mybaitsplus.devtools.core.util.EmailUtil;
import com.mybaitsplus.devtools.core.util.NativeUtil;
import com.yfs.springboot.quartz.model.TaskFireLog;
import com.yfs.springboot.quartz.service.SchedulerService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 调度监听器
 */
@Slf4j
@Component
public class TaskFireLogJobListener implements org.quartz.JobListener {
	@Autowired
	private SchedulerService schedulerService;
	// 线程池
	private static ExecutorService executorService = Executors.newCachedThreadPool();
	private static String JOB_LOG = "jobLog";

	public String getName() {
		return "taskListener";
	}

	public void jobExecutionVetoed(JobExecutionContext context) {
	}

	// 任务开始前
	public void jobToBeExecuted(final JobExecutionContext context) {
		final JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
		String targetObject = jobDataMap.getString("targetObject");
		String targetMethod = jobDataMap.getString("targetMethod");
		String taskName = jobDataMap.getString("taskName");
		String taskGroup = jobDataMap.getString("taskGroup");
		if (log.isInfoEnabled()) {
			log.info("定时任务开始执行：{}.{}", taskGroup, taskName);
		}
		// 保存日志
		TaskFireLog tasklog = new TaskFireLog();
		tasklog.setStartTime(context.getFireTime());
		tasklog.setGroupName(taskGroup);
		tasklog.setTaskName(taskName);
		tasklog.setStatus(Constants.JOBSTATE.INIT_STATS);
		tasklog.setServerHost(NativeUtil.getHostName());
		tasklog.setServerDuid(NativeUtil.getDUID());
		schedulerService.updateLog(tasklog);
		jobDataMap.put(JOB_LOG, tasklog);
	}

	// 任务结束后
	public void jobWasExecuted(final JobExecutionContext context, JobExecutionException exp) {
		Timestamp end = new Timestamp(System.currentTimeMillis());
		final JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
		String targetObject = jobDataMap.getString("targetObject");
		String targetMethod = jobDataMap.getString("targetMethod");
		String taskName = jobDataMap.getString("taskName");
		String taskGroup = jobDataMap.getString("taskGroup");
		if (log.isInfoEnabled()) {
			log.info("定时任务执行结束：{}.{}", taskGroup, taskName);
		}
		// 更新任务执行状态
		final TaskFireLog tasklog = (TaskFireLog) jobDataMap.get(JOB_LOG);
		if (tasklog != null) {
			tasklog.setEndTime(end);
			if (exp != null) {
				String contactEmail = jobDataMap.getString("contactEmail");
				if (StringUtils.isNotBlank(contactEmail)) {
					String topic = String.format("调度[%s.%s]发生异常", taskGroup, taskName);
					sendEmail(new Email(contactEmail, topic, exp.getMessage()));
				}
				tasklog.setStatus(Constants.JOBSTATE.ERROR_STATS);
				tasklog.setFireInfo(exp.getMessage());
			} else {
				if (tasklog.getStatus().equals(Constants.JOBSTATE.INIT_STATS)) {
					tasklog.setStatus(Constants.JOBSTATE.SUCCESS_STATS);
				}
			}
		}
		executorService.submit(new Runnable() {
			public void run() {
				if (tasklog != null) {
					try {
						schedulerService.updateLog(tasklog);
					} catch (Exception e) {
						log.error("Update TaskRunLog cause error. The log object is : " + JSON.toJSONString(tasklog), e.getMessage());
					}
				}
			}
		});
	}

	private void sendEmail(final Email email) {
		executorService.submit(new Runnable() {
			public void run() {
				log.info("将发送邮件至：" + email.getSendTo());
				EmailUtil.sendEmail(email);
			}
		});
	}
}
