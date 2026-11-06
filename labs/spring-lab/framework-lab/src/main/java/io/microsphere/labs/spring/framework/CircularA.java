package io.microsphere.labs.spring.framework;

import org.springframework.beans.factory.annotation.Autowired;

public class CircularA {

    @Autowired
    CircularB circularB;
}
