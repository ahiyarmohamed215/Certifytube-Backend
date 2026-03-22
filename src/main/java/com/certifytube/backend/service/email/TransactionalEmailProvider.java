package com.certifytube.backend.service.email;

public interface TransactionalEmailProvider {

    void send(TransactionalEmailMessage message);
}
