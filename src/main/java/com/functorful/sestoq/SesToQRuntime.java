package com.functorful.sestoq;

import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.micronaut.function.aws.runtime.AbstractMicronautLambdaRuntime;

import java.net.MalformedURLException;
import java.util.Map;

public class SesToQRuntime extends AbstractMicronautLambdaRuntime<Map<String, Object>, Void, Map<String, Object>, Void> {

    public static void main(String[] args) throws MalformedURLException {
        new SesToQRuntime().run(args);
    }

    @Override
    protected RequestHandler<Map<String, Object>, Void> createRequestHandler(String... args) {
        return new SesToQHandler();
    }
}
