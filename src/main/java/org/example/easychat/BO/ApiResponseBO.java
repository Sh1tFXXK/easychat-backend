package org.example.easychat.BO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ApiResponseBO<T> {
    private boolean success;      // 是否成功
    private String message;      // 消息
    private Integer code;        // 状态码
    private T data;// 数据

    public static <T> ApiResponseBO<T> success(T data){
        return new ApiResponseBO<>(true, "success", 200, data);
    }
    
    public static <T> ApiResponseBO<T> error(String message){
        return new ApiResponseBO<>(false, message, 400, null);
    }

    public static <T> ApiResponseBO<T> success(T data, String message){
        return new ApiResponseBO<>(true, message, 200, data);
    }
    
    public static ApiResponseBO<Void> success(String message){
        return new ApiResponseBO<>(true, message, 200, null);
    }


}
