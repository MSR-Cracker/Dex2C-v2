package com.dex2c.model;

import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Method;

import java.util.stream.Collectors;

public record MethodInfo(
        String dexEntry,
        String className,
        String methodName,
        String descriptor,
        int accessFlags
) {
    public static final int ACC_STATIC = 0x0008;
    public static final int ACC_NATIVE = 0x0100;
    public static final int ACC_ABSTRACT = 0x0400;

    public static MethodInfo from(String dexEntry, ClassDef classDef, Method method) {
        String params = method.getParameters().stream()
                .map(parameter -> parameter.getType())
                .collect(Collectors.joining());
        String descriptor = "(" + params + ")" + method.getReturnType();
        return new MethodInfo(
                dexEntry,
                classDef.getType(),
                method.getName(),
                descriptor,
                method.getAccessFlags()
        );
    }

    public String toDexSignature() {
        return className + "->" + methodName + descriptor;
    }

    public String toFilterSignature() {
        return className.substring(1) + methodName + descriptor;
    }

    public boolean isStatic() {
        return (accessFlags & ACC_STATIC) != 0;
    }

    public boolean canWrapNative() {
        return (accessFlags & (ACC_NATIVE | ACC_ABSTRACT)) == 0
                && !methodName.equals("<init>")
                && !methodName.equals("<clinit>");
    }
}
