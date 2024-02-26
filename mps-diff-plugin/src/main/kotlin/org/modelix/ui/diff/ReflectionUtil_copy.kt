package org.modelix.ui.diff

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

object ReflectionUtil_copy {
    fun readField(cls: Class<*>, obj: Any, fieldName: String): Any {
        try {
            val field: Field = cls.getDeclaredField(fieldName)
            field.setAccessible(true)
            return field.get(obj)
        } catch (ex: Exception) {
            throw RuntimeException("Cannot read field '" + fieldName + "' in class '" + cls + "' of object: " + obj, ex)
        }
    }

    fun writeField(cls: Class<*>, obj: Any, fieldName: String, value: Any?) {
        try {
            val field: Field = cls.getDeclaredField(fieldName)
            field.setAccessible(true)
            if (Modifier.isFinal(field.getModifiers())) {
                val modifiersField: Field = Field::class.java.getDeclaredField("modifiers")
                modifiersField.setAccessible(true)
                val originalModifier: Int = field.getModifiers()
                modifiersField.setInt(field, originalModifier and (Modifier.FINAL).inv())
            }
            field.set(obj, value)
        } catch (ex: Exception) {
            throw RuntimeException(
                "Cannot write field '" + fieldName + "' in class '" + cls + "' of object: " + obj,
                ex,
            )
        }
    }

    fun callMethod(
        cls: Class<*>,
        obj: Any?,
        methodName: String,
        argumentTypes: Array<Class<*>?>,
        arguments: Array<Any?>,
    ): Any? {
        try {
            val method: Method = cls.getDeclaredMethod(methodName, *argumentTypes)
            method.setAccessible(true)
            return method.invoke(obj, *arguments)
        } catch (ex: Exception) {
            throw RuntimeException(
                "Cannot call method '" + methodName + "' in class '" + cls + "' of object: " + obj,
                ex,
            )
        }
    }

    fun callVoidMethod(
        cls: Class<*>,
        obj: Any?,
        methodName: String,
        argumentTypes: Array<Class<*>?>,
        arguments: Array<Any?>,
    ) {
        callMethod(cls, obj, methodName, argumentTypes, arguments)
    }

    fun callStaticMethod(
        cls: Class<*>,
        methodName: String,
        argumentTypes: Array<Class<*>?>,
        arguments: Array<Any?>,
    ): Any? {
        return callMethod(cls, null, methodName, argumentTypes, arguments)
    }

    fun callStaticVoidMethod(
        cls: Class<*>,
        methodName: String,
        argumentTypes: Array<Class<*>?>,
        arguments: Array<Any?>,
    ) {
        callStaticMethod(cls, methodName, argumentTypes, arguments)
    }

    fun getClass(fqName: String?): Class<*> {
        try {
            return Class.forName(fqName)
        } catch (ex: ClassNotFoundException) {
            throw RuntimeException("", ex)
        }
    }
}
