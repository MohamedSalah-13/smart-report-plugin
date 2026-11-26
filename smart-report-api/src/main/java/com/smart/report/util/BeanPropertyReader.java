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
 * يقرأ قيمة حقل من أي كائن Java عبر الاسم فقط، بدون الحاجة لتعريف interface خاص:
 * يجرّب getX()، ثم isX() (للـ boolean)، ثم x() (لمكوّنات الـ record)، وأخيرًا الحقل نفسه
 * بالبحث في الصنف وكل أصوله.
 *
 * <p>الـ accessor بيتحدّد مرة واحدة لكل (صنف، اسم حقل) وبيتخزّن، فتقرير فيه 50 ألف صف
 * ما بيعملش بحث reflection لكل خلية. الكاش مبني على {@link ClassValue} فبيتحرّر مع الصنف
 * نفسه ومش بيمسك classloader في الذاكرة.</p>
 */
public final class BeanPropertyReader {

    private BeanPropertyReader() {}

    /** يقرأ قيمة من كائن؛ بيرمي {@link IllegalStateException} لو الخاصية مش موجودة أو تعذّرت قراءتها. */
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
     * يدوّر على getter بدون مُعطيات وقابل فعلًا للاستدعاء. الميثود العام المعرَّف على صنف
     * <b>غير عام</b> بيرمي IllegalAccessException وقت الاستدعاء، فبندوّر على نفس التوقيع في
     * واجهة أو صنف أب عام الأول، وبنلجأ لـ setAccessible لو ملقيناش.
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

    /** يدوّر على الحقل في الصنف وكل أصوله، مش في الصنف المباشر بس. */
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

    /** الأصناف العامة (واجهات وآباء) اللي ممكن نستدعي الميثود من خلالها. */
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
