package org.example.easychat.BO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResponseBO<T> {
    private Boolean success;
    private String message;
    private T data;

    public ResponseBO(Boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static <T> ResponseBO<T> success(String  message){
        return new ResponseBO<>(true, message);
    }

    public static <T> ResponseBO<T> success(T data) {
        return new ResponseBO<>(true, null, data);
    }

    public static <T> ResponseBO<T> success(String message, T data) {
        return new ResponseBO<>(true, message, data);
    }

    public static <T> ResponseBO<T> error(String message) {
        return new ResponseBO<>(false, message);
    }
}
