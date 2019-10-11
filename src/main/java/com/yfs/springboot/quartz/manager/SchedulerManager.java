package com.yfs.springboot.quartz.manager;

import com.mybaitsplus.devtools.core.exception.BusinessException;
import com.mybaitsplus.devtools.core.util.DataUtil;
import com.yfs.springboot.quartz.job.BaseJob;
import com.yfs.springboot.quartz.job.StatefulJob;
import com.yfs.springboot.quartz.model.TaskScheduled;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.quartz.Trigger.TriggerState;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.*;

/**
 * 默认的定时任务管理器
 */
@Slf4j
public class SchedulerManager implements InitializingBean, ApplicationContextAware {

    @Autowired
    private Scheduler scheduler;

    /*@PostConstruct
    public void startScheduler() {
        try {
            scheduler.start();
        } catch (SchedulerException e) {
            e.printStackTrace();
        }
    }*/

    private List<JobListener> jobListeners;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        Map<String, JobListener> beansMap = applicationContext.getBeansOfType(JobListener.class);
        if(beansMap!=null){
            List<JobListener> listeners=new ArrayList<>();
            for(JobListener value : beansMap.values()){
                listeners.add(value);
            }
            if(jobListeners==null || this.jobListeners.size()==0){
                setJobListeners(listeners);
            }
        }
    }

    public void setJobListeners(List<JobListener> jobListeners) {
        this.jobListeners = jobListeners;
    }

    public void afterPropertiesSet() throws Exception {
        if (this.jobListeners != null && this.jobListeners.size() > 0) {
            if (log.isInfoEnabled()) {
                log.info("Initing task scheduler[" + this.scheduler.getSchedulerName() + "] , add listener size ："
                    + this.jobListeners.size());
            }
            for (JobListener listener : this.jobListeners) {
                if (log.isInfoEnabled()) {
                    log.info("Add JobListener : " + listener.getName());
                }
                this.scheduler.getListenerManager().addJobListener(listener);
            }
        }
    }

    public List<TaskScheduled> getAllJobDetail() {
        List<TaskScheduled> result = new LinkedList<TaskScheduled>();
        try {
            GroupMatcher<JobKey> matcher = GroupMatcher.jobGroupContains("");
            Set<JobKey> jobKeys = scheduler.getJobKeys(matcher);
            for (JobKey jobKey : jobKeys) {
                JobDetail jobDetail = scheduler.getJobDetail(jobKey);
                List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);
                for (Trigger trigger : triggers) {
                    TaskScheduled job = new TaskScheduled();
                    job.setTaskName(jobKey.getName());
                    job.setTaskGroup(jobKey.getGroup());
                    TriggerState triggerState = scheduler.getTriggerState(trigger.getKey());
                    job.setStatus(triggerState.name());
                    if (trigger instanceof CronTrigger) {
                        CronTrigger cronTrigger = (CronTrigger)trigger;
                        String cronExpression = cronTrigger.getCronExpression();
                        job.setTaskCron(cronExpression);
                    }
                    job.setPreviousFireTime(trigger.getPreviousFireTime());
                    job.setNextFireTime(trigger.getNextFireTime());
                    JobDataMap jobDataMap = trigger.getJobDataMap();
                    job.setTaskType(jobDataMap.getString("taskType"));
                    job.setTargetSystem(jobDataMap.getString("targetSystem"));
                    job.setTargetObject(jobDataMap.getString("targetObject"));
                    job.setTargetMethod(jobDataMap.getString("targetMethod"));
                    job.setContactName(jobDataMap.getString("contactName"));
                    job.setContactEmail(jobDataMap.getString("contactEmail"));
                    job.setTaskDesc(jobDetail.getDescription());
                    String jobClass = jobDetail.getJobClass().getSimpleName();
                    if (jobClass.equals("StatefulJob")) {
                        job.setJobType("statefulJob");
                    } else if (jobClass.equals("DefaultJob")) {
                        job.setJobType("job");
                    }
                    result.add(job);
                }
            }
        } catch (Exception e) {
            log.error("Try to load All JobDetail cause error : ", e);
        }
        return result;
    }

    public JobDetail getJobDetailByTriggerName(Trigger trigger) {
        try {
            return this.scheduler.getJobDetail(trigger.getJobKey());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    public Trigger getTriggerByTriggerKey(TriggerKey trigger) {
        try {
            return this.scheduler.getTrigger(trigger);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * 新增job
     * 
     * @throws Exception
     */
    public boolean updateTask(TaskScheduled taskScheduled) {
    	String jobGroup = taskScheduled.getTaskGroup();
        if (DataUtil.isEmpty(jobGroup)) {
			jobGroup = "ds_job";
		}
        String jobName = taskScheduled.getTaskName();
        if (DataUtil.isEmpty(jobName)) {
            jobName = String.valueOf(System.currentTimeMillis());
        }
        String cronExpression = taskScheduled.getTaskCron();
        String targetObject = taskScheduled.getTargetObject();
        String targetMethod = taskScheduled.getTargetMethod();
        String jobDescription = taskScheduled.getTaskDesc();
        String jobType = taskScheduled.getJobType();
        String taskType = taskScheduled.getTaskType();
        JobDataMap jobDataMap = new JobDataMap();
        if (TaskScheduled.TaskType.dubbo.equals(taskType)) {
            jobDataMap.put("targetSystem", taskScheduled.getTargetSystem());
        }
        jobDataMap.put("targetObject", targetObject);
        jobDataMap.put("targetMethod", targetMethod);
        jobDataMap.put("taskType", taskType);
        jobDataMap.put("contactName", taskScheduled.getContactName());
        jobDataMap.put("contactEmail", taskScheduled.getContactEmail());
        jobDataMap.put("taskName", taskScheduled.getTaskName());
        jobDataMap.put("taskGroup", taskScheduled.getTaskGroup());

        JobBuilder jobBuilder = null;
        if (TaskScheduled.JobType.job.equals(jobType)) {
            jobBuilder = JobBuilder.newJob(BaseJob.class);
        } else if (TaskScheduled.JobType.statefulJob.equals(jobType)) {
            jobBuilder = JobBuilder.newJob(StatefulJob.class);
        }
        if (jobBuilder != null) {
            JobDetail jobDetail = jobBuilder.withIdentity(jobName, jobGroup).withDescription(jobDescription)
                .storeDurably(true).usingJobData(jobDataMap).build();

            Trigger trigger = TriggerBuilder.newTrigger().withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
                .withIdentity(jobName, jobGroup).withDescription(jobDescription).forJob(jobDetail)
                .usingJobData(jobDataMap).build();

            try {
                JobDetail detail = scheduler.getJobDetail(new JobKey(jobName, jobGroup));

                if (detail == null) {
                    scheduler.scheduleJob(jobDetail, trigger);
                } else {
                    scheduler.addJob(jobDetail, true);
                    TriggerState triggerState = scheduler.getTriggerState(trigger.getKey());
                    scheduler.rescheduleJob(new TriggerKey(jobName, jobGroup), trigger);
                    if(TriggerState.PAUSED==triggerState){//暂停保持原状态
                        scheduler.pauseJob(jobDetail.getKey());
                    }
                }
                return true;
            } catch (SchedulerException e) {
                log.error("SchedulerException", "SchedulerException", e);
                throw new BusinessException(e);
            }
        }
        return false;
    }

    /**
     * 暂停所有触发器
     * 
     * @return
     */
    public void pauseAllTrigger() {
        try {
            scheduler.standby();
        } catch (SchedulerException e) {
            log.error("SchedulerException", "SchedulerException", e);
            throw new BusinessException(e);
        }
    }
    /**
     * 启动所有触发器
     * 
     * @return
     */
    public void startAllTrigger() {
        try {
            if (scheduler.isInStandbyMode()) {
                scheduler.start();
            }
        } catch (SchedulerException e) {
            log.error("SchedulerException", "SchedulerException", e);
            throw new BusinessException(e);
        }
    }

    // 暂停任务
    public void stopJob(TaskScheduled scheduleJob) {
        try {
            JobKey jobKey = JobKey.jobKey(scheduleJob.getTaskName(), scheduleJob.getTaskGroup());
            scheduler.pauseJob(jobKey);
        } catch (Exception e) {
            log.error("Try to stop Job cause error : ", e);
            throw new BusinessException(e);
        }
    }

    // 启动任务
    public void resumeJob(TaskScheduled scheduleJob) {
        try {
            JobKey jobKey = JobKey.jobKey(scheduleJob.getTaskName(), scheduleJob.getTaskGroup());
            scheduler.resumeJob(jobKey);
        } catch (Exception e) {
            log.error("Try to resume Job cause error : ", e);
            throw new BusinessException(e);
        }
    }

    // 执行任务
    public void runJob(TaskScheduled scheduleJob) {
        try {
            JobKey jobKey = JobKey.jobKey(scheduleJob.getTaskName(), scheduleJob.getTaskGroup());
            scheduler.triggerJob(jobKey);
        } catch (Exception e) {
            log.error("Try to resume Job cause error : ", e);
            throw new BusinessException(e);
        }
    }

    // 删除任务
    public void delJob(TaskScheduled scheduleJob) {
        try {
            JobKey jobKey = JobKey.jobKey(scheduleJob.getTaskName(), scheduleJob.getTaskGroup());
            TriggerKey triggerKey = TriggerKey.triggerKey(scheduleJob.getTaskName(), scheduleJob.getTaskGroup());
            scheduler.pauseTrigger(triggerKey);// 停止触发器
            scheduler.unscheduleJob(triggerKey);// 移除触发器
            scheduler.deleteJob(jobKey);// 删除任务
        } catch (Exception e) {
            log.error("Try to resume Job cause error : ", e);
            throw new BusinessException(e);
        }
    }


}
