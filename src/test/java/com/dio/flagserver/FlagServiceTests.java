package com.dio.flagserver;

import com.dio.flagserver.service.FlagService;
import com.dio.flagserver.entity.Flag;
import com.dio.flagserver.mapper.FlagMapper;
import com.dio.flagserver.mapper.WhiteListMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import java.time.Duration;
import java.util.NoSuchElementException;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FlagServiceTests {
    private FlagMapper flagMapper;
    private WhiteListMapper whiteListMapper;
    private JsonMapper jsonMapper;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String,String> operations;
    private FlagService flagService;
    @BeforeEach
    void setUp(){
         flagMapper=mock(FlagMapper.class);//mock后的对象返回值由我来定，默认返回null，false，0
         whiteListMapper=mock(WhiteListMapper.class);
         jsonMapper=mock(JsonMapper.class);
         redisTemplate=mock(StringRedisTemplate.class);
         operations=mock(ValueOperations.class);
         flagService=new FlagService(flagMapper,whiteListMapper,jsonMapper,redisTemplate);
         when(redisTemplate.opsForValue()).thenReturn(operations);//使mock后的redisTemplate在.ops时有operation作返回值，不然调用会报空指针
    }
    @Test
    void disabledFlagShouldReturnFalse(){
        Flag flag=new Flag();
        flag.setId(1L);
        flag.setEnabled(false);
        flag.setRolloutPercent(100);
        //flag id=1 false percent=100
        when(flagMapper.findByFlagKey("new-search")).thenReturn(flag);
        when(whiteListMapper.existsByUserIdAndFlagId(1001L,1L)).thenReturn(true);
        Boolean res= flagService.evaluate("new-search",1001L);
        assertFalse(res);
    }
    @Test
    void  whitelistedUserShouldPassWithZeroRollout(){
        Flag flag =new Flag();
        flag.setId(1L);
        flag.setEnabled(true);
        flag.setRolloutPercent(0);
        when(flagMapper.findByFlagKey("test")).thenReturn(flag);
        when(whiteListMapper.existsByUserIdAndFlagId(1001L,1L)).thenReturn(true);
        Boolean res=flagService.evaluate("test",1001L);
        assertTrue(res);
    }
    @Test
    void  userShouldNotPassWithZeroRollout(){
        Flag flag =new Flag();
        flag.setId(1L);
        flag.setEnabled(true);
        flag.setRolloutPercent(0);
        when(flagMapper.findByFlagKey("test")).thenReturn(flag);
        when(whiteListMapper.existsByUserIdAndFlagId(1001L,1L)).thenReturn(false);
        Boolean res=flagService.evaluate("test",1001L);
        assertFalse(res);
    }
    @Test
    void  userShouldPassWithFullRollout(){
        Flag flag =new Flag();
        flag.setId(1L);
        flag.setEnabled(true);
        flag.setRolloutPercent(100);
        when(flagMapper.findByFlagKey("test")).thenReturn(flag);
        when(whiteListMapper.existsByUserIdAndFlagId(1001L,1L)).thenReturn(false);
        Boolean res=flagService.evaluate("test",1001L);
        assertTrue(res);
    }
    @Test
    void notFindFlagKey(){
        when(flagMapper.findByFlagKey("missing")).thenReturn(null);
        assertThrows(
                NoSuchElementException.class,
                ()->flagService.findByFlagKey("missing")//先执行assertThrows再执行lambda
        );
    }
    @Test
    void repeatedQueryShouldUseCache(){
        Flag flag=new Flag();
        flag.setEnabled(true);
        flag.setFlagKey("cache-test");
        String json = "{\"flagKey\":\"cache-test\",\"enabled\":true}";
        when(flagMapper.findByFlagKey("cache-test")).thenReturn(flag);//返回预设
        when(jsonMapper.writeValueAsString(flag)).thenReturn(json); //flagKey:cache-test,enabled:true
        when(operations.get("flag:cache-test")).thenReturn(null,json);//第一次返回null，第二次返回json
        when(jsonMapper.readValue(json,Flag.class)).thenReturn(flag);
        flagService.findByFlagKey("cache-test");
        flagService.findByFlagKey("cache-test");//缓存
        verify(flagMapper,times(1)).findByFlagKey("cache-test");
    }
    @Test
    void redisReadFailureShouldFallBackToDatabase(){
        Flag flag = new Flag();
        flag.setFlagKey("fallback-test");
        flag.setEnabled(true);

        when(operations.get("flag:fallback-test")).thenThrow(new RedisConnectionFailureException("模拟redis连接失败"));
        when(flagMapper.findByFlagKey("fallback-test")).thenReturn(flag);
        Flag res=flagService.findByFlagKey("fallback-test");

        assertSame(flag,res);//直接查数据库应该一致
        verify(flagMapper,times(1)).findByFlagKey("fallback-test");
        verify(operations,never()).set(anyString(),any(),any(Duration.class));
    }
}
