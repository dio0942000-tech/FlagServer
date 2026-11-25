package com.dio.flagserver.controller;

import com.dio.flagserver.dto.UpdateFlagRequest;
import com.dio.flagserver.service.FlagService;
import com.dio.flagserver.entity.Flag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/flags")
public class FlagController {
    private final FlagService flagService;
    public FlagController(FlagService flagService){
        this.flagService=flagService;
    }
    @GetMapping
    public List<Flag> findAll(){
       return flagService.findAll();
    }
    @PostMapping
    public int add(@RequestBody Flag flag){
        return flagService.add(flag);
    }
    @GetMapping("/{id}")
    public Flag findById(@PathVariable("id") Long id){return flagService.findById(id);}
    @PutMapping("/{id}")
    public Flag updateById(@PathVariable("id") Long id,@RequestBody UpdateFlagRequest request){return flagService.updateById(id,request);}
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteById(@PathVariable("id") Long id){flagService.deleteById(id);}
    @GetMapping("/evaluate/{flagKey}")
    public boolean findEnabledByFlagKey(@PathVariable("flagKey")String flagKey){return flagService.findByFlagKey(flagKey).getEnabled();}
    @GetMapping("/evaluate/{flagKey}/{userId}")
    public boolean evaluate(@PathVariable("flagKey")String flagKey,@PathVariable("userId") Long userId){return flagService.evaluate(flagKey,userId);}
    @PostMapping("/{id}/whitelist/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addWhite(@PathVariable("id") Long id,@PathVariable("userId") Long userId){flagService.addWhite(id,userId);}
    @DeleteMapping("/{id}/whitelist/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWhite(@PathVariable("id") Long id,@PathVariable("userId") Long userId){flagService.deleteWhite(id,userId);}
    @GetMapping("/{id}/whitelist")
    public List<Long> findWhiteById(@PathVariable("id") Long id){return flagService.findWhiteById(id);}
    @GetMapping("/perf-ping")
    public boolean perfPing() {
        return true;
    }
}
