package com.smart.report.util;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads a field value from any Java object by name only, without the need for a special interface.
 * It tries getX(), then isX() (for booleans), then x() (for record components), and finally the field
 * itself, searching the class and all its superclasses.
 *
 * <p>The accessor is resolved once per (class, field name) and cached, so a report with 50,000 rows
 * does not do a reflection lookup for every cell. The cache is built on {@link ClassValue}, so it is
 * released together with the class and does not hold a classloader in memory.</p>
 */
public final class BeanPropertyReader {

    private BeanPropertyReader() {}

    /** Reads a value from an object; throws {@link IllegalStateException} if the property is missing or unreadable. */
    @FunctionalInterface
    private interface Accessor {
        Object read(Object bean) throws ReflectiveOperationException;
    }

    private static final ClassValue<Map<String, Accessor>> ACCESSORS = new ClassValue<>() {
        @Override
        protected Map<String, Accessor> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    public static Object read(Object bean, String fieldName) {
        if (bean == null) {
            return null;
        }
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName is required.");
        }

        Class<?> type = bean.getClass();
        Accessor accessor = ACCESSORS.get(type).computeIfAbsent(fieldName, name -> resolve(type, name));
        try {
            return accessor.read(bean);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(
                    "Reading property '" + fieldName + "' from " + type.getName() + " threw an exception.",
                    e.getCause() != null ? e.getCause() : e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Failed to read property '" + fieldName + "' from " + type.getName(), e);
        }
    }

    public static String readAsString(Object bean, String fieldName) {
        Object value = read(bean, fieldName);
        return value == null ? "" : String.valueOf(value);
    }

    private static Accessor resolve(Class<?> type, String fieldName) {
        String capitalized = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);

        for (String candidate : List.of("get" + capitalized, "is" + capitalized, fieldName)) {
            Method method = findMethod(type, candidate);
            if (method != null) {
                return method::invoke;
            }
        }

        Field field = findField(type, fieldName);
        if (field != null) {
            return field::get;
        }

        // نخزّن الفشل كمان، عشان ما نعيدش البحث الفاشل لكل صف في التقرير
        return bean -> {
            throw new NoSuchMethodException("Property '" + fieldName + "' not found on " + type.getName()
                    + " (tried get" + capitalized + "(), is" + capitalized + "(), " + fieldName
                    + "(), and a field named '" + fieldName + "' on the class and its superclasses).");
        };
    }

    /**
     * Looks for a no-argument getter that can really be invoked. A public method declared on a
     * <b>non-public</b> class throws IllegalAccessException when invoked, so we first look for the same
     * signature on a public interface or superclass, and fall back to setAccessible if none is found.
     */
    private static Method findMethod(Class<?> type, String name) {
        Method method;
        try {
            method = type.getMethod(name);
        } catch (NoSuchMethodException e) {
            return null;
        }
        if (Modifier.isStatic(method.getModifiers()) || method.getReturnType() == void.class) {
            return null;
        }
        if (Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
            return method;
        }

        for (Class<?> supertype : publicSupertypes(type)) {
            try {
                Method alternative = supertype.getMethod(name);
                if (!Modifier.isStatic(alternative.getModifiers())) {
                    return alternative;
                }
            } catch (NoSuchMethodException ignored) {
                // الواجهة دي ما فيهاش الميثود: جرّب اللي بعدها
            }
        }
        return makeAccessible(method) ? method : null;
    }

    /** Looks for the field in the class and all its superclasses, not only in the class itself. */
    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                if (!Modifier.isStatic(field.getModifiers()) && makeAccessible(field)) {
                    return field;
                }
            } catch (NoSuchFieldException ignored) {
                // مش معرّف هنا: كمّل لأب الصنف
            }
        }
        return null;
    }

    /** The public types (interfaces and superclasses) through which the method can be invoked. */
    private static List<Class<?>> publicSupertypes(Class<?> type) {
        List<Class<?>> found = new ArrayList<>();
        Set<Class<?>> seen = new HashSet<>();
        Deque<Class<?>> pending = new ArrayDeque<>();
        pending.add(type);

        while (!pending.isEmpty()) {
            Class<?> current = pending.poll();
            if (current == Object.class || !seen.add(current)) {
                continue;
            }
            if (current != type && Modifier.isPublic(current.getModifiers())) {
                found.add(current);
            }
            Class<?> parent = current.getSuperclass(); // null للواجهات، وArrayDeque بترفض الـ null
            if (parent != null) {
                pending.add(parent);
            }
            Collections.addAll(pending, current.getInterfaces());
        }
        return found;
    }

    private static boolean makeAccessible(AccessibleObject member) {
        try {
            member.setAccessible(true);
            return true;
        } catch (RuntimeException e) {
            return false; // InaccessibleObjectException تحت JPMS، أو SecurityManager
        }
    }
}
