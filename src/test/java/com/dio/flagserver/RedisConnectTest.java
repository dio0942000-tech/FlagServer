package com.dio.flagserver;

import com.dio.flagserver.entity.Flag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.time.Duration;
import java.time.LocalDateTime;

@SpringBootTest
public class RedisConnectTest {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Test
    void test(){
        stringRedisTemplate.opsForValue().set("study:java:name","kabi", Duration.ofSeconds(60));
        String name=stringRedisTemplate.opsForValue().get("study:java:name");
        System.out.println(name);
        assertEquals("kabi",name);
    }
    @Autowired
    private JsonMapper jsonMapper;
    @Test
    void change(){
        Flag flag=new Flag();
        flag.setId(1L);
        flag.setFlagKey("new-search");
        flag.setName("新版搜索");
        flag.setEnabled(true);
        flag.setRolloutPercent(20);
        flag.setCreatedAt(LocalDateTime.of(2026, 11, 11, 10, 0));
        String json=jsonMapper.writeValueAsString(flag);
        stringRedisTemplate.opsForValue().set("study:flag:new-search",json,Duration.ofSeconds(60));
        String cacheJson=stringRedisTemplate.opsForValue().get("study:flag:new-search");
        Flag restored=jsonMapper.readValue(cacheJson,Flag.class);
        System.out.println("开关:"+flag.getFlagKey());
        assertEquals(flag,restored);
    }
}
