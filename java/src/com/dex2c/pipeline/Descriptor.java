package com.dex2c.pipeline;

import java.util.ArrayList;
import java.util.List;

final class Descriptor {
    private Descriptor() {
    }

    static List<String> params(String descriptor) {
        int i = descriptor.indexOf('(') + 1;
        int end = descriptor.indexOf(')');
        List<String> params = new ArrayList<>();
        while (i < end) {
            int start = i;
            while (descriptor.charAt(i) == '[') {
                i++;
            }
            if (descriptor.charAt(i) == 'L') {
                i = descriptor.indexOf(';', i) + 1;
            } else {
                i++;
            }
            params.add(descriptor.substring(start, i));
        }
        return params;
    }

    static String returnType(String descriptor) {
        return descriptor.substring(descriptor.indexOf(')') + 1);
    }

    static String jniType(String type) {
        if (type.startsWith("[") || type.startsWith("L")) {
            return "jobject";
        }
        return switch (type) {
            case "V" -> "void";
            case "Z" -> "jboolean";
            case "B" -> "jbyte";
            case "C" -> "jchar";
            case "S" -> "jshort";
            case "I" -> "jint";
            case "J" -> "jlong";
            case "F" -> "jfloat";
            case "D" -> "jdouble";
            default -> "jobject";
        };
    }

    static String callSuffix(String type) {
        if (type.startsWith("[") || type.startsWith("L")) {
            return "Object";
        }
        return switch (type) {
            case "V" -> "Void";
            case "Z" -> "Boolean";
            case "B" -> "Byte";
            case "C" -> "Char";
            case "S" -> "Short";
            case "I" -> "Int";
            case "J" -> "Long";
            case "F" -> "Float";
            case "D" -> "Double";
            default -> "Object";
        };
    }

    static String jvalueField(String type) {
        if (type.startsWith("[") || type.startsWith("L")) {
            return "l";
        }
        return switch (type) {
            case "Z" -> "z";
            case "B" -> "b";
            case "C" -> "c";
            case "S" -> "s";
            case "I" -> "i";
            case "J" -> "j";
            case "F" -> "f";
            case "D" -> "d";
            default -> "l";
        };
    }
}
