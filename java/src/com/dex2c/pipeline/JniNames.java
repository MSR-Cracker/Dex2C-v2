package com.dex2c.pipeline;

import com.dex2c.model.MethodInfo;

final class JniNames {
    private JniNames() {
    }

    static String longName(MethodInfo method) {
        String cls = method.className().substring(1, method.className().length() - 1);
        return "Java_" + encode(cls) + "_" + encode(method.methodName()) + "__" + encode(shorty(method.descriptor()));
    }

    static String renamedMethod(MethodInfo method) {
        return "d2c$orig$" + method.methodName().replace("<", "_").replace(">", "_");
    }

    private static String shorty(String descriptor) {
        return descriptor.substring(1, descriptor.indexOf(')'));
    }

    private static String encode(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '/':
                    out.append('_');
                    break;
                case '_':
                    out.append("_1");
                    break;
                case ';':
                    out.append("_2");
                    break;
                case '[':
                    out.append("_3");
                    break;
                default:
                    if ((ch >= 'A' && ch <= 'Z')
                            || (ch >= 'a' && ch <= 'z')
                            || (ch >= '0' && ch <= '9')) {
                        out.append(ch);
                    } else {
                        out.append("_0").append(String.format("%04x", (int) ch));
                    }
            }
        }
        return out.toString();
    }
}
