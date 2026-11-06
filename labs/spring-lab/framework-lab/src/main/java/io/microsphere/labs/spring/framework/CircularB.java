package io.microsphere.labs.spring.framework;

import org.springframework.beans.factory.annotation.Autowired;

public class CircularB {

    @Autowired
    CircularA circularA;
}
