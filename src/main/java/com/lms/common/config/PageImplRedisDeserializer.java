package com.lms.common.config;

import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * {@link PageImpl} không có constructor mặc định/{@code @JsonCreator}, và
 * {@link Pageable}/{@code PageRequest} cũng vậy (constructor private) — nên Jackson
 * ghi (PUT) vào Redis được nhưng không bao giờ đọc lại (GET) được, khiến cache
 * "publicCourseSearch" luôn fallback DB (xem {@link CacheConfig}). Tự đọc "content"/
 * "number"/"size"/"totalElements" từ JSON rồi tự dựng lại {@link PageRequest}, bỏ qua
 * việc deserialize object "pageable" lồng bên trong (không cần tới).
 */
final class PageImplRedisDeserializer extends JsonDeserializer<PageImpl<?>> {

    @Override
    public PageImpl<?> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        ObjectCodec codec = p.getCodec();
        JsonNode node = codec.readTree(p);

        List<Object> content = new ArrayList<>();
        JsonNode contentNode = node.get("content");
        if (contentNode != null && contentNode.isArray()) {
            for (JsonNode item : contentNode) {
                content.add(codec.treeToValue(item, Object.class));
            }
        }

        int number = node.hasNonNull("number") ? node.get("number").asInt() : 0;
        int size = node.hasNonNull("size") ? node.get("size").asInt() : Math.max(content.size(), 1);
        long totalElements = node.hasNonNull("totalElements") ? node.get("totalElements").asLong() : content.size();

        Pageable pageable = PageRequest.of(number, Math.max(size, 1));
        return new PageImpl<>(content, pageable, totalElements);
    }
}
