module org.example.finprocessor.predictor {
    requires org.example.finprocessor.predictor.api;
    requires spring.beans;
    requires spring.webflux;
    requires spring.context;
    requires reactor.core;
    requires com.fasterxml.jackson.databind;
    requires io.netty.handler;

    opens org.example.finprocessor.predictor;
}
