# ReBuild

Library to mass compute java frames at runtime

[![](https://www.jitpack.io/v/com.fox2code/ReBuild.svg)](https://www.jitpack.io/#com.fox2code/ReBuild)

## Usage

Just as simple as this, but you may want to cache `ClassDataProvider` instance.

```java
ClassDataProvider classDataProvider = new ClassDataProvider(classLoader);
ClassWriter classWriter = classDataProvider.newClassWriter();
```

Then you use the resulting ClassWriter as usual.

It is made to support computing frame of classes that get loaded,
and made with the explicit goal of being used in a class loader.

If you wish, you can copy ReBuild code into your project as long as it contain a copy of the license. 

The license need to be in the final compiled jar file, naming it as `LICENSE_ReBuild` or 
putting the license in the package where the copied code is located should be good enough, 
as long as the license is in the final jar file.

## Lore

When I initially made rebuild, I just copy-pasted it to my various projects with various improvements,
this one is the one using the improvement I used in my various mod loaders, 
and was like this as far back 2019 until I finally decided to make it a separate library in 2024.

This library was changed to support more differing environments and library setup.
