package com.wilderness.backend.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch 客户端 Bean。
 * 通过应用配置中的 wilderness.ai.elasticsearch.host/port 创建连接。
 */
@Configuration
public class ElasticsearchClientConfig {

    @Bean
    public ElasticsearchClient elasticsearchClient(
            @Value("${wilderness.ai.elasticsearch.host}") String host,
            @Value("${wilderness.ai.elasticsearch.port}") int port) {
        // ES 正常查询是毫秒级响应；读超时给 3 秒足够宽松，同时远小于 CompareService 的
        // 60 秒编排超时（AiModelConfig 里已有先例：底层超时须更短，线程才能真正被释放）。
        RestClient restClient = RestClient.builder(new HttpHost(host, port, "http"))
                .setRequestConfigCallback(builder -> builder
                        .setConnectTimeout(1000)
                        .setSocketTimeout(3000))
                .build();
        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }
}
