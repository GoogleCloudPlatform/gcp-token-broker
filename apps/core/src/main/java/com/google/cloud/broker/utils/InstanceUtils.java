package com.google.cloud.broker.utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class InstanceUtils {

    public static Object invokeConstructor(String className) {
        try {
            Class c = Class.forName(className);
            Constructor constructor = c.getConstructor();
            return constructor.newInstance();
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause == null) {
                throw new RuntimeException(e);
            }
            else {
                throw new RuntimeException(cause);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
