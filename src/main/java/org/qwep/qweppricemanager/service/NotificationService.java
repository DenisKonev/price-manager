package org.qwep.qweppricemanager.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
@Slf4j
public class NotificationService {

    private final RedisTemplate<String, String> redisInternalTemplate;
    private final ObjectMapper mapper;

    @Autowired
    public NotificationService(@Qualifier("redis.internal") RedisTemplate<String, String> redisInternalTemplate, ObjectMapper mapper) {
        this.redisInternalTemplate = redisInternalTemplate;
        this.mapper = mapper;
    }

    public void postMessageToNotificationChanel(String notificationId, String message) {
        if (notificationId != null) {
            String notifications = redisInternalTemplate.opsForValue().get("notifications:" + notificationId);
            try {
                if (notifications != null) {
                    List<String> notificationList = mapper.readValue(notifications, new TypeReference<>() {
                    });
                    notificationList.add(message);
                    redisInternalTemplate.opsForValue().set("notifications:" + notificationId, mapper.writeValueAsString(notificationList), Duration.ofHours(1));
                } else {
                    redisInternalTemplate.opsForValue().set("notifications:" + notificationId, mapper.writeValueAsString(List.of(message)), Duration.ofHours(1));
                }
            } catch (Exception e) {
                log.warn("failed to parse notification list: {}", e.getLocalizedMessage());
            }
        }
    }

    public List<String> getServiceNotificationsById(String notificationId) {
        String notifications = redisInternalTemplate.opsForValue().getAndDelete("notifications:" + notificationId);
        try {
            return mapper.readValue(notifications, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("failed to parse notification list: {}", e.getLocalizedMessage());
            return List.of();
        }
    }
}
