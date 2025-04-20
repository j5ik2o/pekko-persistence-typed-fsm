package com.github.j5ik2o.pekko.persistence.effector.example;

import com.github.j5ik2o.pekko.persistence.effector.javadsl.PersistenceEffector;

public class HelloWorldJava {
    public static void main(String[] args) {
        System.out.println("Hello, World! from Java");
        System.out.println("Using PersistenceEffector library version: " + 
            PersistenceEffector.class.getPackage().getImplementationVersion());
    }
}
