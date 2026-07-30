package com.smart.report.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * يقرأ قيمة حقل من أي كائن Java عبر الاسم فقط، بدون الحاجة لتعريف interface خاص:
 * يجرّب getX()، ثم isX() (للـ boolean)، ثم x() (لمكوّنات الـ record)، وأخيرًا الوصول المباشر للحقل.
 */
public final class BeanPropertyReader {

    private BeanPropertyReader() {}

    public static Object read(Object bean, String fieldName) {
        if (bean == null) return null;

        Class<?> clazz = bean.getClass();
        String capitalized = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);

        for (String candidate : new String[] {"get" + capitalized, "is" + capitalized, fieldName}) {
            try {
                Method method = clazz.getMethod(candidate);
                return method.invoke(bean);
            } catch (NoSuchMethodException ignored) {
                // جرّب التالي
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(
                        "Failed to read property '" + fieldName + "' from " + clazz.getSimpleName(), e);
            }
        }

        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(bean);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Property '" + fieldName + "' not found on " + clazz.getSimpleName());
        }
    }

    public static String readAsString(Object bean, String fieldName) {
        Object value = read(bean, fieldName);
        return value == null ? "" : String.valueOf(value);
    }
}
