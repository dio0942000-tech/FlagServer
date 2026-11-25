package com.dio.flagserver.client;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class FlagClient {
    private final String baseUrl;
    private final HttpClient client;
    public FlagClient(String baseUrl){
        this.baseUrl=baseUrl;
        this.client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }
    public boolean evaluate(String flagKey,Long userId)throws IOException,InterruptedException {
        HttpRequest request=HttpRequest.newBuilder().uri(URI.create(baseUrl+"/api/flags/evaluate/"+flagKey+"/"+userId)).timeout(Duration.ofSeconds(5)).GET().build();
        HttpResponse<String> response=client.send(request,HttpResponse.BodyHandlers.ofString());
        if (response.statusCode()!=200){
            throw new IOException("查询失败:"+response.body());
        }
        return Boolean.parseBoolean(response.body());
    }
}
