package org.example.thrift;

import lombok.Data;

import java.io.Serializable;

@Data
public class ServiceException extends RuntimeException implements Serializable {

    private String code;

    public ServiceException(String code, String description) {
        super(description);
        this.code = code;
    }
}