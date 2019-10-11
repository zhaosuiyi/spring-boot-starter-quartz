package com.yfs.springboot.quartz.job;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.PersistJobDataAfterExecution;

/**
 * 阻塞调度
 * 
 */
@PersistJobDataAfterExecution
@DisallowConcurrentExecution
public class StatefulJob extends BaseJob implements Job {
}
