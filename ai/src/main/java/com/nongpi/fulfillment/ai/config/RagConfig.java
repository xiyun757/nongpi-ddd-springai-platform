package com.nongpi.fulfillment.ai.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * RAG 配置 — PGVector 向量存储（数据源 + VectorStore Bean）
 * <p>
 * PGVector 作为独立数据源（与主库 MySQL 隔离），bge-m3 生成 1024 维向量。
 * RAG advisor 由 {@link com.nongpi.fulfillment.ai.rag.RagAdvisorFactory} 按推断的 category 动态生成。
 * </p>
 */
@Configuration
public class RagConfig {

    /**
     * MySQL 主数据源（业务库）— 显式 @Primary，避免 MyBatis-Plus 误用 pgDataSource
     * <p>读取 spring.datasource.* 配置，与 application.yml 主库一致。</p>
     */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSource dataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password,
            @Value("${spring.datasource.driver-class-name}") String driver) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName(driver);
        ds.setPoolName("mysql-main-pool");
        ds.setMaximumPoolSize(10);
        return ds;
    }

    /**
     * PGVector 专用数据源（指向 pgvector 容器）
     */
    @Bean("pgDataSource")
    @ConfigurationProperties("pgvector.datasource")
    public DataSource pgDataSource(
            @Value("${pgvector.datasource.url}") String url,
            @Value("${pgvector.datasource.username}") String username,
            @Value("${pgvector.datasource.password}") String password,
            @Value("${pgvector.datasource.driver-class-name}") String driver) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName(driver);
        ds.setPoolName("pgvector-pool");
        ds.setMaximumPoolSize(4);
        return ds;
    }

    /**
     * PGVector 专用 JdbcTemplate
     */
    @Bean("pgJdbcTemplate")
    public JdbcTemplate pgJdbcTemplate(@Qualifier("pgDataSource") DataSource pgDataSource) {
        return new JdbcTemplate(pgDataSource);
    }

    /**
     * MySQL 主库 JdbcTemplate — @Primary 关键！
     *
     * <p>一旦显式声明了 pgJdbcTemplate，Spring Boot 的 JdbcTemplateAutoConfiguration
     * 会因 {@code @ConditionalOnMissingBean(JdbcOperations.class)} 而不再自动创建默认
     * JdbcTemplate。若这里不补一个 MySQL 版，其他模块裸注入 {@code JdbcTemplate} 时
     * 唯一候选是 pgJdbcTemplate（连 PG），SQL 全打到 PG → 如
     * {@code LotEventConsumer} 的幂等表 {@code INSERT INTO t_mq_consume_log} 报
     * {@code relation "t_mq_consume_log" does not exist}。</p>
     *
     * <p>标 @Primary 后，无 qualifier 的 {@code JdbcTemplate} 注入默认取 MySQL 主库。</p>
     */
    @Bean("mysqlJdbcTemplate")
    @Primary
    public JdbcTemplate mysqlJdbcTemplate(@Qualifier("dataSource") DataSource mysqlDataSource) {
        return new JdbcTemplate(mysqlDataSource);
    }

    /**
     * 向量存储 — PGVector（bge-m3 = 1024 维，余弦相似度，HNSW 索引）
     * <p>initializeSchema=true 自动建表，启动即可用。</p>
     */
    @Bean
    public VectorStore vectorStore(
            @Qualifier("pgJdbcTemplate") JdbcTemplate pgJdbcTemplate,
            EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(pgJdbcTemplate, embeddingModel)
                .dimensions(1024)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .schemaName("public")
                .vectorTableName("vector_store")
                .initializeSchema(true)
                .build();
    }
}

