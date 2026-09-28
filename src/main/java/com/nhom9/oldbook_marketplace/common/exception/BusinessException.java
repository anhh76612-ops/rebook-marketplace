// File: src/main/java/com/nhom9/oldbook_marketplace/common/exception/BusinessException.java
package com.nhom9.oldbook_marketplace.common.exception;

public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}