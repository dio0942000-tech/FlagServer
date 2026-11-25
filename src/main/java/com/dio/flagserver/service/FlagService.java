package com.dio.flagserver.service;
import com.dio.flagserver.dto.UpdateFlagRequest;
import com.dio.flagserver.entity.Flag;
import com.dio.flagserver.mapper.FlagMapper;
import com.dio.flagserver.mapper.WhiteListMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;
@Slf4j
@Service
public class FlagService {
    private final FlagMapper flagMapper;
    private final WhiteListMapper whiteListMapper;
    private final JsonMapper jsonMapper;
    private final StringRedisTemplate redisTemplate;
    public FlagService(FlagMapper flagMapper,WhiteListMapper whiteListMapper,JsonMapper jsonMapper,StringRedisTemplate redisTemplate){
        this.flagMapper=flagMapper;//后面是spring传入的代理对象
        this.whiteListMapper=whiteListMapper;
        this.jsonMapper=jsonMapper;
        this.redisTemplate=redisTemplate;
    }
    public List<Flag> findAll(){
        return flagMapper.findAll();
    }
    public int add(Flag flag){
        try {
            if (flag.getName() == null || flag.getName().isBlank()) {
                throw new IllegalArgumentException("名称不能为空");
            }
            if (flag.getName().length() > 128) {
                throw new IllegalArgumentException("名称长度不能超过128");
            }
            if (flag.getDescription() != null && flag.getDescription().length() > 512) {
                throw new IllegalArgumentException("描述长度不能超过512");
            }
            if (flag.getEnabled() == null) {
                throw new IllegalArgumentException("开关状态不能为空");
            }
            Integer percent = flag.getRolloutPercent();
            if (percent == null || percent < 0 || percent > 100) {
                throw new IllegalArgumentException("比例不能为空，且必须在0到100之间");
            }
            if (flag.getFlagKey() == null || flag.getFlagKey().isBlank()
                    || flag.getFlagKey().length() > 64
                    || !flag.getFlagKey().matches("[A-Za-z0-9-]+")) {
                throw new IllegalArgumentException("开关标识不能为空，长度不能超过64，只允许英文字母、数字和短横线");
            }
            return flagMapper.add(flag);
        }catch (DuplicateKeyException e){
            throw new DuplicateKeyException("开关已经存在");
        }

    }
    public Flag findById(Long id){
        Flag flag=flagMapper.findById(id);
        if(flag==null){
            throw new NoSuchElementException("没有匹配的开关");
        }
        return flag;
    }
    @Transactional
    public void deleteById(Long id){
        Flag oldFlag= findById(id);
        whiteListMapper.deleteAllWhite(id);//删除一个功能的所有白名单
        flagMapper.deleteById(id);

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit(){
                        try {
                            redisTemplate.delete("flag:" + oldFlag.getFlagKey());//确保缓存的删除运行在事务提交之后
                        }catch (DataAccessException e){
                            log.warn("开关删除成功，删除缓存失败 flagkey={}",oldFlag.getFlagKey(),e);
                        }
                    }
                }
        );
    }
    public Flag updateById(Long id, UpdateFlagRequest request) {
        Flag oldFlag = findById(id);//查询id是否存在的同时记录原本数据库的值
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("名称不能为空");
        }
        if (request.getEnabled() == null) {
            throw new IllegalArgumentException("开关状态不能为空");
        }
        Integer percent = request.getRolloutPercent();
        if (percent == null || percent < 0 || percent > 100) {
            throw new IllegalArgumentException("比例不能为空，且必须在0到100之间");
        }
        if (request.getName().length() > 128 || request.getDescription()!=null &&request.getDescription().length() > 512) {
            throw new IllegalArgumentException("名称长度小于128，描述小于512");
        }
        flagMapper.updateById(id, request);
        try {
            redisTemplate.delete("flag:" + oldFlag.getFlagKey());//删除修改后的缓存
        }catch (DataAccessException e){
            log.warn("开关修改成功，删除缓存失败 flagKey={}", oldFlag.getFlagKey(),e);
        }
        return findById(id);
    }
    public Flag findByFlagKey(String flagKey){
        String cacheKey="flag:"+flagKey;//设置redis中要用的查询键
        String cachedJson=null;
        boolean getCache=true;
        try {
            cachedJson = redisTemplate.opsForValue().get(cacheKey);
        }//从redis缓存中获得json
        catch(DataAccessException e){
            getCache=false;
            log.warn("读取redis失败，转查数据库,key={}",cacheKey,e);
        };//在redis查询失败时正常用数据库

        if(cachedJson!=null){
            return jsonMapper.readValue(cachedJson,Flag.class);
        }
        Flag flag=flagMapper.findByFlagKey(flagKey);
        if(flag==null){
            throw new NoSuchElementException("没找到对应开关标识");
        }
        Boolean enabled=flag.getEnabled();
        if (flag.getEnabled() == null) {
            throw new IllegalStateException("开关数据异常：enabled 不能为空");
        }
        if(getCache) {
            try {
                String json=jsonMapper.writeValueAsString(flag);//把数据库查到的flag对象转为json
                redisTemplate.opsForValue().set(cacheKey, json, Duration.ofSeconds(10));
            } catch (DataAccessException e) {
                log.warn("缓存写入失败,返回数据库查询结果={}", cacheKey, e);
            }//防止存入失败不返回数据
        }
        return flag ;
    }
    public Boolean evaluate(String flagKey, Long userId){
        Flag flag=findByFlagKey(flagKey);
        if(!flag.getEnabled()){
            return false;
        }
        if(whiteListMapper.existsByUserIdAndFlagId(userId, flag.getId())){
            return true;
        }
        String input=flagKey+":"+userId;
        int bucket= Math.floorMod(input.hashCode(),100);
        return bucket < flag.getRolloutPercent();
    }
    public Boolean addWhite(Long id,Long userId){
        findById(id);
        try{
            return whiteListMapper.addWhite(id,userId)>0;
        } catch (DuplicateKeyException e) {
            throw new DuplicateKeyException("白名单已经存在该用户",e) ;
        }
    }
    public void deleteWhite(Long id,Long userId){
        findById(id);
        int rows= whiteListMapper.deleteWhite(id,userId);
        if(rows==0){
            throw new NoSuchElementException("没找到对应的白名单用户");
        }
    }
    public List<Long> findWhiteById(Long flagId){
        findById(flagId);
        return whiteListMapper.findWhiteById(flagId);
    }
}
