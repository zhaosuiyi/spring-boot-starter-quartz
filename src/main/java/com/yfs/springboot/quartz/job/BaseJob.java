package com.yfs.springboot.quartz.job;

import com.mybaitsplus.devtools.core.base.BaseProvider;
import com.mybaitsplus.devtools.core.base.Parameter;
import com.mybaitsplus.devtools.core.util.SpringContextUtil;
import com.yfs.springboot.quartz.utils.DubboUtil;
import com.yfs.springboot.quartz.model.TaskScheduled;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.context.ApplicationContext;
import java.lang.reflect.Method;

/**
 * 
 */
@Slf4j
public class BaseJob implements Job {

	public void execute(JobExecutionContext context) throws JobExecutionException {
		long start = System.currentTimeMillis();
		JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
		String taskType = jobDataMap.getString("taskType");
		String targetObject = jobDataMap.getString("targetObject");
		String targetMethod = jobDataMap.getString("targetMethod");
		try {
			ApplicationContext applicationContext= SpringContextUtil.getApplicationContext();
			if (TaskScheduled.TaskType.spring.equals(taskType)) {
				Object refer = applicationContext.getBean(targetObject);
				refer.getClass().getDeclaredMethod(targetMethod).invoke(refer);
			} else if (TaskScheduled.TaskType.dubbo.equals(taskType)) {
				String system = jobDataMap.getString("targetSystem");
				BaseProvider provider = (BaseProvider) DubboUtil.refer(applicationContext, system);
				provider.execute(new Parameter(targetObject, targetMethod));
			} else if (TaskScheduled.TaskType.local.equals(taskType)) {
				invokMethod(targetObject,targetMethod);
			}
		} catch (Exception e) {
			throw new JobExecutionException(e);
		}finally {
			double time = (System.currentTimeMillis() - start) / 1000.0;
			log.info("定时任务[{}.{}]用时：{}s", targetObject, targetMethod, time);
		}
	}
	
	public void invokMethod(String targetClass,String targetMethod) throws Exception, Exception{
		Object object = null;
		Class clazz = null;
		clazz = Class.forName(targetClass);
		object = clazz.newInstance();
		clazz = object.getClass();
		Method method = null;
		method = clazz.getDeclaredMethod(targetMethod);
		method.invoke(object);
	}

	private static final String APPLICATION_CONTEXT_KEY = "applicationContextKey";
	private ApplicationContext getApplicationContext(JobExecutionContext context) throws Exception {
		ApplicationContext appCtx = null;
		appCtx = (ApplicationContext) context.getScheduler().getContext().get(APPLICATION_CONTEXT_KEY);
		if (appCtx == null) {
			throw new JobExecutionException("No application context available in scheduler context for key \"" + APPLICATION_CONTEXT_KEY + "\"");
		}
		return appCtx;
	}

}
