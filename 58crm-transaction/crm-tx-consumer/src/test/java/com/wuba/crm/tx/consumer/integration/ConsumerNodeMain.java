package com.wuba.crm.tx.consumer.integration;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.wuba.crm.tx.consumer.bootstrap.ConsumerBootstrap;
import com.wuba.crm.tx.consumer.bootstrap.ConsumerRuntimeConfig;
import com.wuba.crm.tx.consumer.persistence.ConsumerRecordMapper;
import com.wuba.crm.tx.consumer.persistence.IdempotentMapper;
import com.wuba.crm.tx.consumer.persistence.MybatisConsumerRecordRepository;
import com.wuba.crm.tx.consumer.persistence.MybatisIdempotentRepository;
import com.wuba.crm.tx.consumer.worker.HandlerRegistry;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class ConsumerNodeMain {

    private ConsumerNodeMain() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parse(args);
        String mysqlUrl = required(options, "mysqlUrl");
        String mysqlUser = options.getOrDefault("mysqlUser", "root");
        String mysqlPassword = options.getOrDefault("mysqlPassword", "");
        String redisHost = options.getOrDefault("redisHost", "127.0.0.1");
        int redisPort = Integer.parseInt(options.getOrDefault("redisPort", "6379"));
        String streamPrefix = options.getOrDefault("streamPrefix", "tx:it:");
        String groupPrefix = options.getOrDefault("groupPrefix", "tx-it-group-");
        String bizCode = required(options, "bizCode");
        String consumerId = required(options, "consumerId");
        int workers = Integer.parseInt(options.getOrDefault("workers", "8"));
        int pool = Integer.parseInt(options.getOrDefault("pool", "8"));

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(mysqlUrl);
        hikariConfig.setUsername(mysqlUser);
        hikariConfig.setPassword(mysqlPassword);
        hikariConfig.setMaximumPoolSize(pool);
        hikariConfig.setMinimumIdle(pool);
        HikariDataSource dataSource = new HikariDataSource(hikariConfig);

        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration(redisHost, redisPort);
        LettuceConnectionFactory redisFactory = new LettuceConnectionFactory(redisConfig);
        redisFactory.afterPropertiesSet();
        redisFactory.start();
        StringRedisTemplate redis = new StringRedisTemplate(redisFactory);

        MybatisConfiguration mybatis = new MybatisConfiguration();
        mybatis.addMapper(ConsumerRecordMapper.class);
        mybatis.addMapper(IdempotentMapper.class);
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(mybatis);
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
        SqlSessionTemplate sqlSessionTemplate = new SqlSessionTemplate(sqlSessionFactory);
        MybatisConsumerRecordRepository repository = new MybatisConsumerRecordRepository(
                sqlSessionTemplate.getMapper(ConsumerRecordMapper.class));
        MybatisIdempotentRepository idempotent = new MybatisIdempotentRepository(
                sqlSessionTemplate.getMapper(IdempotentMapper.class));
        HandlerRegistry handlers = new HandlerRegistry();
        handlers.register(bizCode, "LOAD_EVENT", message -> {
        });
        TransactionTemplate transactions = new TransactionTemplate(
                new DataSourceTransactionManager(dataSource));

        ConsumerRuntimeConfig config = new ConsumerRuntimeConfig(
                bizCode, consumerId, streamPrefix, groupPrefix,
                50_000, 500, Duration.ofMillis(100),
                500, workers, Duration.ofSeconds(60), Duration.ofMinutes(5),
                Duration.ofSeconds(30), 3, Duration.ofMillis(10), Duration.ofSeconds(20),
                500, 100_000, Duration.ofMillis(100),
                100, Duration.ofSeconds(30), 500, Duration.ofSeconds(1));
        ConsumerBootstrap bootstrap = new ConsumerBootstrap(
                config, repository, idempotent, handlers, transactions, redis);
        bootstrap.start();
        System.out.println("NODE_READY bizCode=" + bizCode + " consumer=" + consumerId);
        System.out.flush();

        CountDownLatch shutdown = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            bootstrap.close();
            redisFactory.destroy();
            dataSource.close();
            shutdown.countDown();
        }, "consumer-node-shutdown"));
        shutdown.await(24, TimeUnit.HOURS);
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (String arg : args) {
            int index = arg.indexOf('=');
            if (index > 0) {
                options.put(arg.substring(0, index), arg.substring(index + 1));
            }
        }
        return options;
    }

    private static String required(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
