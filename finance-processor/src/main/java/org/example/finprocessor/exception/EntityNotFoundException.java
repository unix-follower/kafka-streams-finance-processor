package org.example.finprocessor.exception;

import org.example.finprocessor.api.ErrorCode;

public class EntityNotFoundException extends AppException {
    public EntityNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
}
