package com.dio.flagserver;

import com.dio.flagserver.service.FlagService;
import com.dio.flagserver.entity.Flag;
import com.dio.flagserver.mapper.FlagMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
public class FlagDeletionIntegrationTests {
    @Autowired
    private FlagService flagService;
    @Test
    void shouldQueryTestDatabase(){
        assertNotNull(flagService.findAll());
    }
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @BeforeEach
    void checkDatabase(){
        String databaseName= jdbcTemplate.queryForObject("select Database()",String.class);
        assertEquals("feature_flag_test",databaseName);
    }
    @Test
    void deleteFlagShouldDeleteWhiteList(){
        Flag flag=new Flag();
        flag.setFlagKey("delete-test-" + System.nanoTime());
        flag.setName("用于测试删除的开关");
        flag.setEnabled(true);
        flag.setRolloutPercent(0);
        flagService.add(flag);
        Long id=flag.getId();
        flagService.addWhite(id,1001L);
        flagService.deleteById(id);
        Integer flagCount=jdbcTemplate.queryForObject("select count(*) from flag where id=?",Integer.class,id);
        Integer whiteListCount=jdbcTemplate.queryForObject("select count(*) from flag_whitelist where flag_id=?", Integer.class,id);
        assertEquals(0,flagCount);
        assertEquals(0,whiteListCount);
    }
    @MockitoSpyBean
    private FlagMapper flagMapper;
    @Test
    void failedDeletionShouldRollbackWhitelist() {
        // 创建开关、取得 id、添加白名单
            Flag flag = new Flag();
            flag.setFlagKey("delete-test-" + System.nanoTime());
            flag.setName("用于测试删除的开关");
            flag.setEnabled(true);
            flag.setRolloutPercent(0);
            flagService.add(flag);
            Long id = flag.getId();
        try{
            flagService.addWhite(id, 1001L);
            doThrow(new IllegalStateException("模拟删除开关失败"))
                    .when(flagMapper)
                    .deleteById(id);
            assertThrows(
                    IllegalStateException.class,
                    () -> flagService.deleteById(id)
            );

            Integer flagCount = jdbcTemplate.queryForObject("select count(*) from flag where id=?", Integer.class, id);
            Integer whiteListCount = jdbcTemplate.queryForObject("select count(*) from flag_whitelist where flag_id=?", Integer.class, id);
            assertEquals(1, flagCount);
            assertEquals(1, whiteListCount);
        }finally {
                jdbcTemplate.update("delete from flag_whitelist where flag_id=?",id);
                jdbcTemplate.update("delete from flag where id=?",id);
        }
    }
}
