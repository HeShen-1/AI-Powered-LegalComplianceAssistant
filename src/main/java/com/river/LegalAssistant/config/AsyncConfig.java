package com.river.LegalAssistant.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务配置
 * 配置支持SecurityContext传递的异步执行器，解决SSE等异步请求的认证问题
 */
@Configuration
@EnableAsync
@Slf4j
@org.springframework.context.annotation.EnableAspectJAutoProxy(exposeProxy = true)
public class AsyncConfig {

    /**
     * 配置异步执行器，支持SecurityContext传递
     * 使用DelegatingSecurityContextAsyncTaskExecutor包装，确保异步线程可以访问SecurityContext
     */
    @Bean(name = "generalTaskExecutor")
    public Executor generalTaskExecutor() {
        log.info("初始化支持SecurityContext的异步执行器");
        
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数
        executor.setCorePoolSize(10);
        
        // 最大线程数
        executor.setMaxPoolSize(50);
        
        // 队列容量
        executor.setQueueCapacity(100);
        
        // 线程名称前缀
        executor.setThreadNamePrefix("Async-");
        
        // 拒绝策略：由调用线程处理
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // 等待所有任务完成后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        
        // 等待时间
        executor.setAwaitTerminationSeconds(60);
        
        executor.initialize();
        
        // 包装成支持SecurityContext的执行器
        return new DelegatingSecurityContextAsyncTaskExecutor(executor);
    }
}
