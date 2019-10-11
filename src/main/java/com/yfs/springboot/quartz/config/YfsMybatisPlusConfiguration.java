package com.yfs.springboot.quartz.config;


import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceBuilder;
import com.baomidou.mybatisplus.MybatisConfiguration;
import com.baomidou.mybatisplus.plugins.PaginationInterceptor;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.mybaitsplus.devtools.core.datasource.framework.interceptor.TableSplitInterceptor;
import com.yfs.springboot.quartz.QuartzAnnotationBeanPostProcessor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.JdbcType;
import org.mybatis.spring.annotation.MapperScan;
import org.quartz.ee.servlet.QuartzInitializerListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.quartz.QuartzDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Slf4j
@Configuration
@MapperScan(basePackages={YfsMybatisPlusConfiguration.MAPPER_PACKAGES},sqlSessionFactoryRef="yfsSqlSessionFactory")
public class YfsMybatisPlusConfiguration {
    /**
     * mapper 所在包
     */
    public static final String MAPPER_PACKAGES = "com.yfs.**.mapper";
    /**
     * MAPPER_LOCATIONS
     */
    public static final String MAPPER_LOCATIONS= "classpath*:yfs/mappers/**/*.xml";
    /**
     * 实体类 所在包
     */
    public static final String ENTITY_PACKAGES = "com.yfs.**.model";
    /**
     * 分页插件
     */
    @Bean
    public PaginationInterceptor paginationInterceptor() {
        return new PaginationInterceptor();
    }

    @Bean("yfsSqlSessionFactory")
    public SqlSessionFactory yfsSqlSessionFactory(@Qualifier("quartzDataSource") DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean sqlSessionFactory = new MybatisSqlSessionFactoryBean();
        sqlSessionFactory.setDataSource(dataSource);
        sqlSessionFactory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources(YfsMybatisPlusConfiguration.MAPPER_LOCATIONS));
        sqlSessionFactory.setTypeAliasesPackage(YfsMybatisPlusConfiguration.ENTITY_PACKAGES);

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setJdbcTypeForNull(JdbcType.NULL);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setCacheEnabled(false);

        sqlSessionFactory.setConfiguration(configuration);
        sqlSessionFactory.setPlugins(new Interceptor[]{
                paginationInterceptor() //添加分页功能
        });
        return sqlSessionFactory.getObject();
    }

    @Bean(name = "quartzDataSource")
    @QuartzDataSource
    @ConfigurationProperties(prefix = "quartz.datasource" )
    public DataSource quartzDataSource() {
        return DruidDataSourceBuilder.create().build();
    }


    /**
     * 配置@Transactional注解事物
     */
    /*@Bean("yfsTransactionManager")
    public DataSourceTransactionManager yfsTransactionManager(@Qualifier("quartzDataSource")DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }*/

    @Bean
    public QuartzInitializerListener executorListener() {
        return new QuartzInitializerListener();
    }

    @Bean
    public ThreadPoolTaskExecutor threadPoolTaskExecutor() {
        ThreadPoolTaskExecutor threadPoolTaskExecutor = new ThreadPoolTaskExecutor();
        threadPoolTaskExecutor.setCorePoolSize(20);
        threadPoolTaskExecutor.setMaxPoolSize(50);
        threadPoolTaskExecutor.setQueueCapacity(100);

        return threadPoolTaskExecutor;
    }
}

