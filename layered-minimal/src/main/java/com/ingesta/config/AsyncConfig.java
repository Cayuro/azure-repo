package com.ingesta.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "eventoIngestaExecutor")
    public Executor eventoIngestaExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("evento-ingesta-");
        // Con la politica por defecto (AbortPolicy), al llenarse la cola el proxy
        // @Async lanza TaskRejectedException EN EL HILO QUE LLAMA, es decir en el
        // hilo HTTP: la peticion del usuario terminaba en 500 aunque la transaccion
        // ya estuviera persistida. Medido en carga: 46 de 150 peticiones fallaban.
        // Con CallerRunsPolicy el hilo que llama ejecuta la tarea el mismo, lo que
        // ralentiza esa peticion pero nunca la rompe y ademas frena la entrada de
        // trabajo nuevo mientras el pool se pone al dia.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
