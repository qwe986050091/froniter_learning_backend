package org.example.thrift;

import lombok.Data;

import java.io.Serializable;

@Data
public class Response implements Serializable {

    private String code;

    private String message;

    private String data;

    public static Response success(String data) {
        Response resp = new Response();
        resp.setCode("200");
        resp.setMessage("success");
        resp.setData(data);
        return resp;
    }

    public static Response error(String code, String message) {
        Response resp = new Response();
        resp.setCode(code);
        resp.setMessage(message);
        return resp;
    }
}