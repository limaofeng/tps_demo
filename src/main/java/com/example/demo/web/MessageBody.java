package com.example.demo.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 接收算法分析的请求
 *
 * @author limaofeng
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MessageBody {

    private String algorithm;

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("task_id")
    private String taskId;

    @Builder.Default private MessageProcessingMode mode = MessageProcessingMode.async;

    @JsonDeserialize(using = MessageBodyInputDeserializer.class)
    private JsonNode input;
}