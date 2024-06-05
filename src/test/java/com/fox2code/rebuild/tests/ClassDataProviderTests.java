package com.fox2code.rebuild.tests;

import com.fox2code.rebuild.ClassData;
import com.fox2code.rebuild.ClassDataProvider;
import org.junit.jupiter.api.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ClassDataProviderTests {
    private final ClassDataProvider classDataProvider =
            new ClassDataProvider(ClassDataProviderTests.class.getClassLoader());

    @Test
    @Order(1)
    public void checkGetClassDataSimple() {
        ClassData classData = classDataProvider.getClassData(ExampleClass.class.getName());
        Assertions.assertEquals(classData.getName(), ExampleClass.class.getName());
        Assertions.assertEquals(classData.getSuperclass().getName(), ExampleClass.class.getSuperclass().getName());
        Assertions.assertEquals(classData.getModifiers(), ExampleClass.class.getModifiers());
    }

    @Test
    @Order(2)
    public void checkGetClassDataArray() throws ClassNotFoundException {
        Class.forName(Object[].class.getName());
        ClassData classData1 = classDataProvider.getClassData(Object[].class.getName());
        Assertions.assertEquals(classData1.getName(), Object[].class.getName());
        Class.forName(String[].class.getName());
        ClassData classData2 = classDataProvider.getClassData(String[].class.getName());
        Assertions.assertEquals(classData2.getName(), String[].class.getName());
    }

    @Test
    @Order(3)
    public void checkMakeClassWriter() {
        Assertions.assertNotNull(classDataProvider.newClassWriter());
    }
}
