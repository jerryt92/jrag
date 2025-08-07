package io.github.jerryt92.jrag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class ThreadPoolConfig {
    public static final String AI_EDGE_ASYNC_TASK_THREAD_POOL = "ai-edge-async-task-thread-pool";
    public static final String NETTY_SERVER_THREAD_POOL = "netty-server-thread-pool";

    @Bean(AI_EDGE_ASYNC_TASK_THREAD_POOL)
    public TaskExecutor inventoryAsyncTaskThreadPool() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setThreadNamePrefix(AI_EDGE_ASYNC_TASK_THREAD_POOL);
        pool.setCorePoolSize(1);//核心线程数
        pool.setMaxPoolSize(1);//最大线程数
        pool.setKeepAliveSeconds(60 * 5);// 设置线程活跃时间（秒）
        pool.setQueueCapacity(10000);//线程队列
        pool.initialize();//线程初始化
        return pool;
    }

    @Bean(NETTY_SERVER_THREAD_POOL)
    public Executor nettyServerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix(NETTY_SERVER_THREAD_POOL);
        executor.initialize();
        return executor;
    }
}