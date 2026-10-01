// File: src/main/java/com/nhom9/oldbook_marketplace/common/exception/BusinessException.java
package com.oldbook.exception.common;

public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}