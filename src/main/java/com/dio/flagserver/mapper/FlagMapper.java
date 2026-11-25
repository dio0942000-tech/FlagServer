package com.dio.flagserver.mapper;
import com.dio.flagserver.dto.UpdateFlagRequest;
import com.dio.flagserver.entity.Flag;
import org.apache.ibatis.annotations.*;
import java.util.List;


@Mapper
public interface FlagMapper {
    @Select("""
            SELECT id,
                   flag_key AS flagKey,
                   name,
                   description,
                   enabled,
                   rollout_percent AS rolloutPercent,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM flag
            ORDER BY id
            """)
    List<Flag> findAll();//这里的只是起了函数名，方法由mybatis创建的实现类对象实现@select
    @Insert("""
            insert into flag
            (flag_key, name, description, enabled, rollout_percent)
            values
            (#{flagKey}, #{name}, #{description}, #{enabled}, #{rolloutPercent})
            """)
    @Options(useGeneratedKeys = true,keyProperty = "id")
    int add(Flag flag);
    @Select("""
            select id,
                   flag_key AS flagKey,
                   name,
                   description,
                   enabled,
                   rollout_percent AS rolloutPercent,
                   created_at AS createdAt,
                   updated_at AS updatedAt
                   from flag
                   where id=#{id}
            """)
    Flag findById(@Param("id") Long id);
    @Select("""
            select id,
                   flag_key AS flagKey,
                   name,
                   description,
                   enabled,
                   rollout_percent AS rolloutPercent,
                   created_at AS createdAt,
                   updated_at AS updatedAt
                   from flag
            where flag_key=#{flagKey}
            """)
    Flag findByFlagKey(@Param("flagKey") String flagKey);
    @Update("""
            update flag
            set name=#{request.name},description=#{request.description},enabled=#{request.enabled},rollout_percent=#{request.rolloutPercent}
            where id=#{id}
            """)
    int updateById(@Param("id") Long id,@Param("request") UpdateFlagRequest request);
    @Delete("""
            delete from flag
            where id=#{id}
            """)
    int deleteById(@Param("id") Long id);

}
