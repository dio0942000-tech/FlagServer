package com.dio.flagserver.cache;
import com.dio.flagserver.entity.Flag;
import lombok.Data;
@Data
public class FlagCacheEntry {
    private Flag flag;
    private long cachedAt;
    public FlagCacheEntry(Flag flag, long cachedAt){
        this.flag=flag;
        this.cachedAt = cachedAt;
    }

}
