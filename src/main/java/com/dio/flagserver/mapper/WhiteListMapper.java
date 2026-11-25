package com.dio.flagserver.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface WhiteListMapper {
    @Select("""
            select exists(
            select 1
            from flag_whitelist
            where user_id=#{userId} and flag_id=#{flagId}
            )
            """)
    Boolean existsByUserIdAndFlagId(@Param("userId") Long userId, @Param("flagId")Long flagId);
    @Insert("""
            insert into flag_whitelist
            (flag_id,user_id)
            values
            (#{flagId},#{userId})
            """)
    int addWhite(@Param("flagId") Long flagId,@Param("userId")Long userId);
    @Delete("""
            delete from flag_whitelist
            where flag_id=#{flagId}
            and user_id=#{userId}
            """)
    int deleteWhite(@Param("flagId") Long flagId,@Param("userId")Long userId);
    @Select("""
            select user_id from flag_whitelist
            where flag_id=#{flagId}
            """)
    List<Long> findWhiteById(@Param("flagId") Long flagId);
    @Delete("""
            delete from flag_whitelist
            where flag_id=#{flagId}
            """)
    int deleteAllWhite(@Param("flagId") Long flagId);
}
